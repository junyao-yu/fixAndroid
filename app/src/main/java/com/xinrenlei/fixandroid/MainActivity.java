package com.xinrenlei.fixandroid;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

/**
 * Auth：yujunyao
 * Since: 2020/12/24 4:59 PM
 * Email：yujunyao@xinrenlei.net
 */

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView textView = findViewById(R.id.textView);

        textView.setText(Utils.testFunction());
    }
}
