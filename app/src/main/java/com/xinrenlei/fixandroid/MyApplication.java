package com.xinrenlei.fixandroid;

import android.app.Application;

/**
 * Auth：yujunyao
 * Since: 2020/12/24 4:58 PM
 * Email：yujunyao@xinrenlei.net
 */

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FixLoadUtils.fixLoad(this);
    }
}
