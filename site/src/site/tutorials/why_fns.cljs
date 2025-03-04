(ns site.tutorials.why-fns
  (:require
   [herb.core :refer [<class <id] :as herb]
   [site.components.text :refer [text]]
   [site.components.code :refer [code]]
   [site.snippets.state-fn :as e1]
   [site.components.paper :refer [paper]]
   [garden.units :refer [rem em px]]
   [reagent.debug :as d]
   [reagent.core :as r])
  (:require-macros
   [site.macros :as macros]))

(defn main
  []
  (let [e1 (macros/example-src "state_fn.cljs")]
    [paper {:id "why-fns"}
     [text {:variant :heading}
      "Why functions?"]
     [text
      "The whole idea for Herb came from the fact that alot of the time I needed
      some kind of stateful CSS. I had a component or element that in some way
      or another needed its style changed based on some state. There are a bunch
      of ways to deal with this, but most of them involve either inline styles
      or swapping out classnames, either of which is ideal in my opinion."]
     [text
      "The way Herb tackles this is by using functions as the style delivery
      method so to speak."]
     [code {:lang :clojure}
      e1]
     [text {:variant :subheading}
      "Output:"]
     [e1/button]
     #_[text
      "A new style element has been added to the DOM, and " [:code "herb"] "
     ensures that only that element is updated when clicking the button."]]))
