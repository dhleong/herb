(ns herb.runtime
  (:require [goog.dom :as dom]
            [goog.object :as gobj]
            [garden.core :refer [css]]
            [garden.selectors :as s]))

(def dev? ^boolean js/goog.DEBUG)

(defonce
  ^{:private true
    :doc "Atom containing all styles added to DOM. Takes the form of a map with
  classnames as keys. The map entry contains a `:data` which is Herb's
  representation of a style unit, `:data-string` which is what is used as the
  style data attribute in DOM, and `:css` which contains the rendered CSS
  string."}
  injected-styles (atom {}))

(defonce
  ^{:private true
    :doc "Atom containing all keyframe CSS added to DOM. Takes the form of a map
  with a namespace as a key. A map entry contains the keys `:data` which is herb's
  representation of a keyframe unit and `:css` which is the rendered CSS
  string"}
  injected-keyframes (atom {}))

(defonce
  ^{:private true
    :doc "Atom containing all global style added to DOM. Takes the form of a map
  with namespace as keys. A map entry contains `:data` which is a collection of
  global styles for a given via defglobal call and `:css` that contains the
  rendered CSS"}
  injected-global (atom {}))

(defonce
  ^{:doc "Atom containing a map with options passed from `herb.core/init!`.
  Entry includes `:vendors` and `:auto-prefix`"}
  options (atom {}))

(defn- update-state
  "Either update a style in state, or create it depending on existing state."
  [state ident data css element data-string]
  (let [css (if-let [old (get @injected-styles ident)]
              (str (:css old) "\n" css)
              css)]
    (-> (assoc-in state [ident :data (first data)] (last data))
        (assoc-in [ident :data-string] data-string)
        (assoc-in [ident :element] element)
        (assoc-in [ident :css] css))))

(defn- reset-style-object!
  "Reset a given style object by passing ident, and if cljs reset style
  element. Used when updating a style."
  [ident element]
  (swap! injected-styles dissoc ident)
  (gobj/set element "innerHTML" ""))

(defn- render-style!
  "Renders CSS, and appends to DOM. Ensure state is in sync with DOM."
  [identifier new]
  (let [style (let [[classname {:keys [style pseudo media supports prefix vendors combinators]}] (:data new)]
                [[classname (with-meta style {:prefix prefix :vendors vendors})
                  pseudo media supports]
                 [(map (fn [[[combinator & elements] style]]
                         (case combinator
                           :> [(apply s/> classname elements) style]
                           :+ [(apply s/+ classname elements) style]
                           :- [(apply s/- classname elements) style]
                           :descendant [(apply s/descendant classname elements) style]
                           (throw (ex-info "Unsupported combinator function "
                                           {:combinator combinator
                                            :elements elements
                                            :style style}))))
                       combinators)]])
        css-str (css {:vendors (seq (:vendors @options))
                      :pretty-print? dev?
                      :auto-prefix (seq (:auto-prefix @options))}
                     style)]
    (dom/append (:element new) (str "\n" css-str))
    (swap! injected-styles update-state
           identifier
           (:data new)
           css-str
           (:element new)
           (:data-string new))))


(defn- create-element!
  "Create an element in the DOM with an optional data-herb attribute"
  [attr]
  (let [head (.-head js/document)]
    (assert (some? head) "An head element is required in the dom to inject the style.")
    (let [element (.createElement js/document "style")]
      (.setAttribute element "type" "text/css")
      (when attr
        (.setAttribute element "data-herb" attr))
      (.appendChild head element)
      element)))

(defn- create-style!
  "Create a style element in head if identifier is not already present Attach a
  data attr with namespace and call render-style with new element"
  [identifier new data-str]
  (let [element (create-element! data-str)]
    (render-style! identifier (cond-> {:data new :element element}
                                data-str (assoc :data-string data-str)))))


(defn inject-style!
  "Main interface to runtime. Takes an identifier, new garden style data
  structure, fully qualified name, and whether its a group.  Make sure
  to only update where necessary, with concessions for
  stylegroups. Returns the injected style state object."
  [identifier new data-str group]
  (let [injected (get @injected-styles identifier)
        target (get (:data injected) (first new))]

    (cond

      ;; Not present in state create-style
      (not injected)
      (create-style! identifier new data-str)

      ;; element created but new classname
      (and (some? injected)
           (not target))
      (render-style!
       identifier
       {:data new
        :element (:element injected)
        :data-string data-str})

      ;; Updating, if its a group make sure that the entire group is
      ;; updated
      (and (some? injected)
           (some? target)
           (not= target (last new)))
      (do (reset-style-object! identifier (:element injected))
          (if group
            (doseq [g (assoc (:data injected) (first new) (last new))]
              (render-style!
               identifier
               {:data g
                :element (:element injected)
                :data-string data-str}))
            (render-style!
             identifier
             {:data new
              :element (:element injected)
              :data-string data-str}))))

    ;; Return state
    (get @injected-styles identifier)))

(defn inject-obj!
  "Inject collection of style objects in a common element, used by passing a
  dispatch in the form of :keyframes or :global"
  [sym dispatch & obj]
  (let [state (case dispatch
                :global injected-global
                :keyframes injected-keyframes)]
    (when-not (= (:data (get @state sym)) obj)
      (let [css-str (css {:pretty-print? dev?} obj)]
        (let [element (or (.querySelector js/document (str "style[data-herb=\"" (name dispatch) "\"]"))
                          (create-element! (name dispatch)))
              inner-html (gobj/get element "innerHTML")]
          (gobj/set element "innerHTML" (str inner-html (when dev? "\n") css-str)))
        (swap! state assoc sym {:data obj :css css-str})))))
