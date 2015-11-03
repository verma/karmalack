(ns karmalack.views
  (:require [sablono.core :as html :refer-macros [html]]
            [om.core :as om :refer-macros [component]]
            [karmalack.state :as state]
            [karmalack.routes :as routes]
            [cljs.core.async :as async :refer [<!]]
            cljsjs.chartist)
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn loader-widget [{:keys [message]} _]
  (component
    (html
      [:.loader
       [:i.loader-widget.fa.fa-spinner.fa-pulse.fa-4x]
       [:h4 message]])))

(defn mini-profile-view [{:keys [id presence name avatar-small settings karma]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:a.mini-profile.clearfix
         {:href (routes/user {:id id})}
         [:.image.pull-left {:style {:background (str "url(" avatar-small ")")}}]
         [:.name.pull-left
          {:class (when (= presence "active")
                    "online")
           :href  (routes/user {:id id})}
          name]

         (when-not (clojure.string/blank? (:skill settings))
           [:h4.skill.pull-left (:skill settings)])

         (when karma
           [:.karma.pull-right [:.count karma]])]))))

(defn- filter-users [users filter]
  (if (clojure.string/blank? filter)
    users
    (->> users
         (keep (fn [u]
                 (let [idx (.indexOf (:name u) filter)]
                   (when-not (neg? idx)
                     [idx u]))))
         (sort-by first)
         (map second))))

(defn home [data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (om/set-state! owner :loading? true)
      (go (<! (state/load-users!))
          (om/set-state! owner :loading? false)))

    om/IRenderState
    (render-state [_ {:keys [filter loading?]}]
      (let [users (om/observe owner state/all-users)
            filtered-users (if-not (clojure.string/blank? filter)
                             (filter-users @users filter)
                             (sort-by :karma #(compare %2 %1) @users))

            handle-change (fn [e]
                            (let [text (.. e -target -value)]
                              (om/set-state! owner :filter text)))]
        (html
          [:.container.home
           [:.row
            [:.col-xs-12
             [:h1.title.clearfix
              [:i.fa.fa-slack.slack.pull-left]
              [:.pull-left "techcorridor.io"]
              [:.page-title.pull-right "Profiles & Ranking"]
              [:input {:type        "text"
                       :value       filter
                       :autoFocus   true
                       :on-change   handle-change
                       :placeholder "Type here to filter"}]]
             (if loading?
               [:i.loading.fa.fa-spinner.fa-pulse.fa-2x]
               (if-let [s (seq filtered-users)]
                 (om/build-all mini-profile-view s {:key :id})
                 [:.no-data.no-users "No users."]))]]])))))

(def ^:private patterns
  ["http://cdn.backgroundhost.com/backgrounds/subtlepatterns/escheresque_ste.png"
   "http://cdn.backgroundhost.com/backgrounds/subtlepatterns/hixs_pattern_evolution.png"
   "http://cdn.backgroundhost.com/backgrounds/subtlepatterns/gun_metal.png"
   "http://cdn.backgroundhost.com/backgrounds/subtlepatterns/skin_side_up.png"
   "http://cdn.backgroundhost.com/backgrounds/subtlepatterns/subtle_orange_emboss.png"
   "http://cdn.backgroundhost.com/backgrounds/subtlepatterns/dark_wood.png"])

(defn user-view-banner [{:keys [username skill banner avatar]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:bg (rand-nth patterns)})

    om/IRenderState
    (render-state [_ {:keys [bg]}]
      (let [banner-style (if (clojure.string/blank? banner)
                           {:background-image  (str "url(" bg ")")
                            :background-size   "auto auto"
                            :background-repeat "repeat"}
                           {:background-image (str "url(" banner ")")
                            :background-size  "cover"})]
        (html
          [:.banner
           {:style banner-style}
           [:.user-info
            [:.useravatar
             {:style {:background-image (str "url(" avatar ")")}}]
            [:.username username]
            [:.skill skill]]])))))

(defn karma-stats [{:keys [total up down]} owner]
  (component
    (html [:.karma-stats
           [:.total total]
           [:.up up
            [:i.fa.fa-arrow-up]]
           [:.down down
            [:i.fa.fa-arrow-down]]])))

(defn- create-distribution-by [stats f]
  (reverse
    (sort-by second #(compare %2 %1)
             (->> stats
                  (map f)
                  frequencies))))

(defn score-distribution [{:keys [f-dist-by prefix]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [node (om/get-node owner)
            stats (om/get-props owner :stats)
            dist (create-distribution-by stats f-dist-by)
            labels (map #(str (or prefix "#") (first %)) dist)
            vals (map second dist)
            _ (println stats labels vals)
            chart (js/Chartist.Bar
                    node
                    (clj->js {:labels labels
                              :series vals})
                    (clj->js {:horizontalBars false
                              :reverseData true
                              :onlyInteger true
                              :divisor 4
                              :distributeSeries true
                              :axisX {:showGrid false}
                              :axisY {:showGrid true
                                      :offset 0
                                      :showLabel false}
                              }))]

        (om/set-state! owner :chart chart)))

    om/IDidUpdate
    (did-update [_ _ _]
      (println "update!")

      )

    om/IRender
    (render [_]
      (html [:.score-dist]))))

(defn user-content-pane [title child]
  [:.content-panel.panel.panel-default
   [:.panel-heading [:h4 title]]
   [:.panel-body child]])


(defn compute-karma-stats [stats]
  (let [up (keep (fn [[_ _ d]] (when (pos? d) d)) stats)
        down (keep (fn [[_ _ d]] (when (neg? d) d)) stats)
        tup (apply + up)
        tdown (apply + down)]
    {:total (+ tup tdown)
     :up tup
     :down (js/Math.abs tdown)}))

(defn- enough-data? [s]
  (> (count s) 0))

(defn user-view [data owner]
  (reify
    om/IRender
    (render [_]
      (let [cu (om/observe owner state/current-user)
            stats (seq (:stats @cu))]
        (html
          [:.user-view
           (om/build user-view-banner {:username (when-let [n (:name @cu)]
                                                   (str "@" n))
                                       :skill    (:skill @cu)
                                       :avatar   (:avatar @cu)
                                       :banner   (:banner @cu)})
           [:.container.user-stats
            [:.row
             [:.col-xs-6
              (user-content-pane
                "Karma"
                [:div
                 [:.tip "Karma earnings."]
                 (om/build karma-stats
                           (if stats
                             (compute-karma-stats stats)
                             {:total 0 :up 0 :down 0}))])]


             [:.col-xs-6
              (user-content-pane
                "Score Distribution"
                (if (enough-data? stats)
                  [:div
                   [:.tip "Top channels where karma points come from."]
                   (om/build score-distribution {:stats     (vec stats)
                                                 :f-dist-by first})]
                  [:.no-data "Not enough data."]))]]

            [:.row
             [:.col-xs-6
              (user-content-pane
                "Top Awardees"
                (if (enough-data? stats)
                  [:div
                   [:.tip "Most liked by."]
                   (om/build score-distribution {:stats     (vec stats)
                                                 :prefix "@"
                                                 :f-dist-by second})]
                  [:.no-data "Not enough data."]))]


             [:.col-xs-6
              ]]
            ]])))))

