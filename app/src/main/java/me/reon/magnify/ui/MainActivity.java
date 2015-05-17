package me.reon.magnify.ui;

import android.app.Activity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import me.reon.magnify.R;
import me.reon.magnify.view.MagnifyLayout;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MagnifyLayout layout = (MagnifyLayout) findViewById(R.id.main);
        TextView tv = (TextView) findViewById(R.id.text);
        layout.addListenerForChildView(tv);
    }
}
