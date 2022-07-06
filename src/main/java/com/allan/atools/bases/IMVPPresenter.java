package com.allan.atools.bases;

public abstract class IMVPPresenter <V extends IMVPView> {
    protected V mMvpView;

    /**
     * 绑定V层
     *
     * @param view
     */
    public void attachMvpView(V view) {
        this.mMvpView = view;
    }

    /**
     * 解除绑定V层
     */
    public void detachMvpView() {
        mMvpView = null;
    }

    /**
     * 获取V层
     *
     * @return
     */
    public V getMvpView() {
        return mMvpView;
    }

    public abstract void load(String url);
}
