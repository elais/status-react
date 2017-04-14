(ns status-im.accounts.screen
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [re-frame.core :refer [dispatch dispatch-sync]]
            [status-im.accounts.styles :as st]
            [status-im.components.text-input-with-label.view :refer [text-input-with-label]]
            [status-im.components.status-bar :refer [status-bar]]
            [status-im.components.toolbar-new.view :refer [toolbar]]
            [status-im.components.toolbar-new.actions :as act]
            [status-im.components.common.common :as common]
            [status-im.components.action-button.action-button :refer [action-button]]
            [status-im.utils.listview :as lw]
            [status-im.constants :refer [console-chat-id]]
            [status-im.components.react :refer [view
                                                text
                                                list-view
                                                list-item
                                                image
                                                touchable-highlight]]
            [status-im.i18n :as i18n]
            [clojure.string :as str]))

(defn account-bage [address photo-path name]
  [view {:flex-direction :row :align-items :center
         :padding-horizontal 16}
   [image {:source {:uri (if (str/blank? photo-path) :avatar photo-path)}
           :style  st/photo-image}]
   [view {:margin-left 16 :flex-shrink 1}
    [text {:style {:font-size 17 :line-height 20 :letter-spacing -0.2}
           :numberOfLines 1}
     (or name address)]]])

(defn account-view [{:keys [address photo-path name] :as account}]
  [view
   [touchable-highlight {:on-press #(dispatch [:open-login address photo-path name])}
    [view {:background-color :white :border-radius 8 :height 64 :justify-content :center}
     [account-bage address photo-path name]]]])

(defn- create-account [_]
  (dispatch-sync [:reset-app])
  (dispatch [:navigate-to :chat console-chat-id]))

(defview accounts []
  [accounts [:get :accounts]]
  (let [accounts (vals accounts)]
    [view st/accounts-container
     [status-bar {:type :transparent}]
     [toolbar {:background-color :transparent
               :hide-border?     true
               :title-style      {:color :white}
               :nav-action       act/nothing
               :actions          [{:image :blank}]
               :title            "Sign in to Status"}]
     [view {:flex 1 :margin-top 10 :margin-bottom 10 :margin-horizontal 16}
      [list-view {:dataSource      (lw/to-datasource accounts)
                  :renderSeparator #(list-item ^{:key %2} [view {:height 10}])
                  :renderRow       #(list-item [account-view %])}]]
     [view st/bottom-actions-container
      [action-button (i18n/label :create-new-account) :test create-account
       {:label-style {:color :white} :cyrcle-color "#ffffff33"}]
      [common/separator {:background-color "#7482eb" :opacity 1 :margin-left 72} {:background-color "#5b6dee"}]
      [action-button (i18n/label :t/recover-access) :test #(dispatch [:navigate-to :recover])
       {:label-style {:color :white} :cyrcle-color "#ffffff33"}]]]))