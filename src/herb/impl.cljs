(ns herb.impl
  (:require
   [clojure.string :as str]
   [herb.runtime :as runtime]
   [clojure.set :as set]
   [garden.stylesheet :refer [at-media at-keyframes at-supports]]))

(def dev? ^boolean js/goog.DEBUG)

(defn- convert-pseudo
  [pseudos]
  (map
   (fn [[kw p]]
     [(keyword (str "&" kw)) p])
   pseudos))

(defn- convert-media
  [media]
  (map (fn [[query style]]
         (at-media query [:& style]))
       media))

(defn- convert-supports
  [supports]
  (map (fn [[query style]]
         (at-supports query [:& style]))
       supports))

(defn convert-vendors
  [vendors]
  (mapv name vendors)
  #_(into []
        (comp
         (map name)
         (distinct))
        vendors))

(defn- resolve-style-fns
  "Calls each function provided in a collection of style-fns. Input can take
  multiple forms depending on how it got called from the consumer side either
  using the macro directly or via extend meta data. Takes a collection of
  `style-fns`  and returns a vector with the resolved style maps."
  [style-fns]
  (loop [sf style-fns
         result []]
    (if (empty? sf)
      result
      (let [input (first sf)]
        (cond
          (fn? input)
          (conj result (apply input (rest sf)))

          (and (coll? input) (fn? (first input)))
          (let [style-fn (first input)
                style-args (rest input)]
            (recur
             (rest sf)
             (conj result (apply style-fn style-args))))
          :else (recur
                 (rest sf)
                 (into result (resolve-style-fns input))))))))

(defn- process-meta-xform
  "Return a transducer that pulls out a given meta type from a sequence and filter
  out nil values"
  [meta-type]
  (comp
   (map meta)
   (map meta-type)
   (filter identity)))

(defn- extract-extended-styles
  "Extract all the `:extend` meta, ensuring what we walk the entire tree, passing
  each extend vector of style-fns to `resolve-style-fns` for resolution. Returns
  vector of resolved styles"
  [style-fns]
  (loop [sf style-fns
         result []]
    (cond

      (fn? sf)
      (recur [sf] result)

      (and (vector? sf) (not (empty? sf)))
      (let [styles (resolve-style-fns sf)
            new-meta (into [] (process-meta-xform :extend) styles)]
        (recur new-meta
               (into styles result)))
      :else result)))

(defn- extract-meta
  "Takes a group of resolved styles and a meta type. Pull out each meta obj and
  merge to prevent duplicates, finally convert to garden acceptable input and
  return"
  [styles meta-type]
  (let [convert-fn (case meta-type
                     :media convert-media
                     :supports convert-supports
                     :prefix identity
                     :vendors convert-vendors
                     :pseudo convert-pseudo
                     :combinators identity)
        extracted (into [] (process-meta-xform meta-type) styles)]
    (when (seq extracted)
      (let [merged (case meta-type
                     :prefix (last extracted)
                     :vendors (apply concat extracted)
                     (apply merge {} extracted))]
        (convert-fn merged)))))


(defn- prepare-data
  "Prepare `resolved-styles` so they can be passed to `garden.core/css` Merge
  the styles to remove duplicate entries and ensuring precedence. Extract all
  meta and return a final vector of styles including meta."
  [resolved-styles]
   {:style (apply merge {} resolved-styles)
    :pseudo  (extract-meta resolved-styles :pseudo)
    :vendors (extract-meta resolved-styles :vendors)
    :prefix (extract-meta resolved-styles :prefix)
    :supports (extract-meta resolved-styles :supports)
    :media (extract-meta resolved-styles :media)
    :combinators (extract-meta resolved-styles :combinators)})

(defn- sanitize
  "Takes `input` and remove any non-valid characters"
  [input]
  (when input
    (cond
      (keyword? input) (sanitize (name input))
      :else (str/replace (str input) #"[^A-Za-z0-9-_]" "_"))))

(defn- compose-selector
  [n k]
  (str (sanitize n)
       (when k
         (str "_" (sanitize k)))))

(defn- create-data-string
  "Create a fully qualified name string for use in the data-herb attr"
  [n k]
  (let [c (str/split n #"/")
        ns (apply str (interpose "." (butlast c)))
        sym (last c)]
    (str (symbol ns sym)
         (when k (str "[" k "]")))))

(defn- get-name
  [style-fn ns-name style-data]
  (let [name* (.-name style-fn)
        anon? (empty? name*)
        hash* (when anon? (.abs js/Math (hash style-data)))
        cname (cond
                (and anon? (not dev?))
                (str "A-" hash*)

                (and dev? anon?)
                (str ns-name "/" "anonymous-" hash*)
                :else name*)]
    (demunge cname)))

(defn with-style!
  "Entry point for macros.
  Takes an `opt` map as first argument, and currently only supports `:id true`
  which appends an id identifier instead of a class to the DOM"
  [{:keys [id? style?]} fn-name ns-name style-fn & args]
  (let [resolved-styles (extract-extended-styles (into [style-fn] args))
        style-data (prepare-data resolved-styles)
        {:keys [group key prefix] :as meta-data} (-> resolved-styles last meta)
        name* (get-name style-fn ns-name style-data)
        data-str (when dev? (if group
                              (create-data-string name* nil)
                              (create-data-string name* key)))
        selector (compose-selector name* key)
        identifier (if group
                     (sanitize name*)
                     selector)
        style-data [(str (if id? "#" ".") selector) style-data]
        result (runtime/inject-style! identifier style-data data-str group)]
    (if style?
      (:css result)
      selector)))
