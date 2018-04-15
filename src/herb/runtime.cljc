(ns herb.runtime
  (:require #?@(:clj []
                :cljs [[goog.dom :as dom]
                       [goog.object :as gobj]])
            [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn break]]
            [garden.core :refer [css]]
            [clojure.string :as str]))

(defonce injected-styles (atom {}))

(defn update-style
  "Create css string and update DOM"
  [identifier #?(:cljs element) new]
  (let [css-str (css (map (fn [[class {:keys [style mode media]}]]
                            [class style mode media])
                          (:data new)))]
    (swap! injected-styles assoc identifier new)
    #?(:cljs (set! (.-innerHTML element) css-str))))

(defn create-style-element
  "Create a style element in head if identifier is not already present Attach a
  data attr with namespace and call update-style with new element"
  [identifier new data-str]
  #?(:cljs
     (let [head (.-head js/document)
           element (.createElement js/document "style")]
       (assert (some? head)
               "An head element is required in the dom to inject the style.")
       (.setAttribute element "type" "text/css")
       (.setAttribute element "data-herb" data-str)
       (.appendChild head element)
       (update-style identifier element {:data (conj {} new) :element element}))
     :clj (update-style identifier {:data (conj {} new)})))

(defn inject-style
  "Main interface to runtime. Takes an identifier, new garden style data structure
  and a fully qualified name. Check if identifier exist in DOM already, and if it
  does, compare `new` with `current` to make sure garden is not called to create
  the same style string again"
  [identifier new data-str]
  (if-let [injected (get @injected-styles identifier)]
    (let [data (:data injected)
          target (get data (key new))]
      (when (not= target (val new))
        (let [element (:element injected)]
          (update-style identifier #?(:cljs element) (assoc injected :data (conj data new))))))
    (create-style-element identifier new data-str)))

(defn set-global-style
  "CLJS: Takes a collection of Garden style vectors, and create or update the global style element
  CLJ: Returns garden.core/css on input"
  [& styles]
  #?(:cljs
     (let [element (.querySelector js/document "style[data-herb=\"global\"]")
           head (.-head js/document)
           css-str (css styles)]
       (assert (some? head) "An head element is required in the dom to inject the style.")
       (if element
         (set! (.-innerHTML element) css-str)
         (let [element (.createElement js/document "style")]
           (set! (.-innerHTML element) css-str)
           (.setAttribute element "type" "text/css")
           (.setAttribute element "data-herb" "global")
           (.appendChild head element))))
     :clj (css styles)))
