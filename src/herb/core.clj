(ns herb.core
  (:require [herb.spec]
            [garden.types])
  (:import [garden.types CSSAtRule])
  )

(defmacro defkeyframes
  "Define a CSS @keyframes animation:
  ```clojure
  (defkeyframes my-animation
        [:from
         {:background \"red\"}]

        [:to
         {:background \"yellow\"}])
  ```
  CLJS: the keyframes CSS gets injected into head under data-herb=\"keyframes\"
  CLJ: Use `<keyframes` macro with the defined keyframes returns a CSS string
       containing the animation"
  [sym & frames]
  (let [value {:identifier `(str '~sym)
               :frames `(list ~@frames)}
        s# `'~sym
        n# (name (ns-name *ns*))
        obj `(CSSAtRule. :keyframes ~value)]
    `(do
       (herb.runtime/inject-obj! (str ~n# "/" ~s#) :keyframes ~obj)
       (def ~sym ~obj))))


(defmacro defglobal
  "Define global CSS:
  ```clojure
  (defglobal some-global-style
    [:body {:box-sizing \"border-box\"
            :font-size (px 14)
    [:button {:border \"none\"}])
  ```
  The CSS output of garden style vectors gets appended to head under
  data-herb=\"global\"
  "

  [sym & styles]
  (let [styles# `(list ~@styles)
        s# `'~sym
        n# (name (ns-name *ns*))]
    `(do
       (herb.runtime/inject-obj! (str ~n# "/" ~s# ) :global ~styles#)
       (def ~sym ~styles#))))


(defmacro <keyframes
  "Returns a CSS string from defined keyframes using the defkeyframes macro.
  ```clojure
  (defkeyframes pulse
    [:from {:opacity 1}]
    [:to {:opacity 0}])

  user=> (<keyframes pulse)
  @keyframes anime {

    from {
      opacity: 1;
    }
    to {
      opacity: 0;
    }
  }
  ```"
  [sym]
  (let [s# `'~sym
        n# (name (ns-name *ns*))]
    `(-> @herb.runtime/injected-keyframes
         (get (str ~n# "/" ~s#))
         :css)))

(defmacro defgroup
  "Define a style group, everything defined in a group is grouped in the same
  style element, It takes a name and a map of styles in the form:
  ```clojure
  (defgroup my-group
    {:a-component {:color \"red\"}})
  ```
  To use a group, use one of `<class` or `<id` macro, where the first argument is
  the key for whatever component stylesheet you want:
  ```clojure
  [:div {:class (<class my-group :a-component)}]
  ```"
  [n c]
  `(defn ~n [~'component & ~'args]
     (if-let [style# (get ~c ~'component)]
       (vary-meta
        style#
        assoc
        :key ~'component
        :group true)
       (throw (str "Herb error: failed to get component: " ~'component " in stylegroup: " '~n)))))


(defmacro <style
  "Takes a function `style-fn` that returns a map. Arguments `args` can be passed
  along with the function as additional arguments to <style i.e
  `(<style some-fn arg1 arg2)`.
  Returns a CSS string that is the result of calling passed function"
  [style-fn & args]
  (let [f `'~style-fn
        n (name (ns-name *ns*))]
    `(cond
       (not (fn? ~style-fn))
       (throw (ex-info (str "herb error in ns \"" ~n "\" the first argument to <style needs to be a function.")
                       {:function ~f
                        :namespace ~n
                        :return-value (~style-fn ~@args)}))
       (not (map? (~style-fn ~@args)))
       (throw (ex-info (str "herb error: style function \"" ~n "/" ~f "\" needs to return a map.")
                       {:function ~f
                        :namespace ~n
                        :return-value (~style-fn ~@args)}))
       :else (herb.impl/with-style! {:style? true} ~f ~n ~style-fn ~@args))))


(defmacro <id
  "Takes a function `style-fn` that returns a map. Arguments `args` can be passed
  along with the function as additional arguments to <id i.e
  `(<id some-fn arg1 arg2)`.
  Returns a unique id based on the fully qualified name of the passed function "
  [style-fn & args]
  (let [f `'~style-fn
        n (name (ns-name *ns*))]
    `(cond
       (not (fn? ~style-fn))
       (throw (ex-info (str "herb error in ns \"" ~n "\" the first argument to <id needs to be a function.")
                       {:function ~f
                        :namespace ~n
                        :return-value (~style-fn ~@args)}))
       (not (map? (~style-fn ~@args)))
       (throw (ex-info (str "herb error: style function \"" ~n "/" ~f "\" needs to return a map.")
                       {:function ~f
                        :namespace ~n
                        :return-value (~style-fn ~@args)}))
       :else (herb.impl/with-style! {:id? true} ~f ~n ~style-fn ~@args))))


(defmacro <class
  "Takes a function `style-fn` that returns a map. Arguments `args` can be passed
  along with the function as additional arguments to <class i.e
  `(<class some-fn arg1 arg2)`.
  Returns a unique class based on the fully qualified name of the passed function"
  [style-fn & args]
  (let [f `'~style-fn
        n (name (ns-name *ns*))]
    `(cond
       (not (fn? ~style-fn))
       (throw (ex-info (str "herb error in ns \"" ~n "\" the first argument to <class needs to be a function.")
                       {:function ~f
                        :namespace ~n
                        :return-value (~style-fn ~@args)}))
       (not (map? (~style-fn ~@args)))
       (throw (ex-info (str "herb error: style function \"" ~n "/" ~f "\" needs to return a map.")
                       {:function ~f
                        :namespace ~n
                        :return-value (~style-fn ~@args)}))
       :else (herb.impl/with-style! {} ~f ~n ~style-fn ~@args))))
