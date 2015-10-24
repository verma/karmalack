(ns karmalack.views
  (:require [sablono.core :as html :refer-macros [html]]
            [om.core :as om]))


(defn- format-today [n]
  (if (neg? n)
    (str "(" n ")")
    (str "(+" n ")")))

(defn mini-profile-view [{:keys [name skill score today]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:.mini-profile.clearfix
         [:.image.pull-left]
         [:h3.name.pull-left name]
         [:h4.skill.pull-left skill]
         [:.karma.pull-right [:.count score] [:.today (format-today today)]]]))))

(defn home [data owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:.container.home
         [:.row
          [:.col-xs-12
           [:h1.title.clearfix
            [:i.fa.fa-slack.slack.pull-left]
            [:.pull-left "techcorridor.io"]
            [:.page-title.pull-right "Profiles & Ranking"]]
           (om/build mini-profile-view {:name "James Bond"
                                        :skill "Spying & Death Defying"
                                        :score 33
                                        :today 4})
           (om/build mini-profile-view {:name "Bruce Wayne"
                                        :skill "Such rich many skillz"
                                        :score 12
                                        :today -3})]]]))))

