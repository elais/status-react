(ns status-im.accounts.login.screen
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [re-frame.core :refer [dispatch dispatch-sync]]
            [status-im.accounts.styles :as ast]
            [status-im.accounts.screen :refer [account-bage]]
            [status-im.components.text-input-with-label.view :refer [text-input-with-label]]
            [status-im.components.status-bar :refer [status-bar]]
            [status-im.components.toolbar-new.view :refer [toolbar]]
            [status-im.components.toolbar-new.actions :as act]
            [status-im.components.react :refer [view
                                                text
                                                touchable-highlight]]
            [status-im.i18n :as i18n]))

(defview login []
  [{:keys [address photo-path name password error]} [:get :login]]
  [view ast/accounts-container
   [status-bar {:type :transparent}]
   [toolbar {:background-color :transparent
             :hide-border?     true
             :title-style      {:color :white}
             :nav-action       (act/back-white #(dispatch [:navigate-back]))
             :actions          [{:image :blank}]
             :title            "Sign in to Status"}]
   [view {:flex 1 :margin-top 10 :margin-bottom 10 :margin-horizontal 16}
    [view {:background-color :white :padding-top 16
           :border-radius 8 :height 150}
     [account-bage address photo-path name]
     [view {:height 8}]
     [text-input-with-label {:label             (i18n/label :t/password)
                             :auto-capitalize   :none
                             :hide-underline?   true
                             :on-change-text    #(do
                                                   (dispatch [:set-in [:login :password] %])
                                                   (dispatch [:set-in [:login :error] ""]))
                             :auto-focus        true
                             :secure-text-entry true
                             :error             (when (pos? (count error)) (i18n/label :t/wrong-password))}]]
    [view {:margin-top 16}
     [touchable-highlight {:on-press #(dispatch [:login-account address password])}
      [view {:background-color "#424fae" :align-items :center :justify-content :center
             :border-radius 8 :height 52}
       [text {:style {:font-size 17 :line-height 20 :letter-spacing -0.2 :color :white}} "Sign in"]]]]]])
