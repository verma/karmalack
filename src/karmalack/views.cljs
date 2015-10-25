(ns karmalack.views
  (:require [sablono.core :as html :refer-macros [html]]
            [om.core :as om]
            [karmalack.state :as state]))


(defn- format-today [n]
  (if (neg? n)
    (str "(" n ")")
    (str "(+" n ")")))

(defn mini-profile-view [{:keys [presence name skill image]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:.mini-profile.clearfix
         [:.image.pull-left {:style {:background (str "url(" image ")")}}]
         [:h3.name.pull-left
          {:class (when (= presence "active")
                    "online")}
          name]
         [:h4.skill.pull-left skill]
         #_(when (or score today)
           [:.karma.pull-right [:.count score]
            (when today
              [:.today (format-today today)])])]))))

(defn home [data owner]
  (reify
    om/IRender
    (render [_]
      (let [users (om/observe owner state/all-users)]
        (html
          [:.container.home
           [:.row
            [:.col-xs-12
             [:h1.title.clearfix
              [:i.fa.fa-slack.slack.pull-left]
              [:.pull-left "techcorridor.io"]
              [:.page-title.pull-right "Profiles & Ranking"]
              [:input. {:type        "text"
                        :autoFocus   true
                        :placeholder "Type here to filter"}]]
             (om/build-all mini-profile-view @users {:key :id})]]])))))

