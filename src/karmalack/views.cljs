(ns karmalack.views
  (:require [sablono.core :as html :refer-macros [html]]
            [om.core :as om]
            [karmalack.state :as state]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn mini-profile-view [{:keys [presence name image settings karma]} owner]
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
            filtered-users (filter-users @users filter)

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
                 [:.no-users "No users."]))]]])))))

