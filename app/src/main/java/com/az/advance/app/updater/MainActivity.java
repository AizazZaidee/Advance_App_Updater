package com.az.advance.app.updater;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView)findViewById(R.id.helloWorld);
        tv.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
                UpdateCheck.getInstance().checkForUpdate(getApplicationContext(),"http://YourServer",true,new UpdateCheck.UpdateCheckCallback() {
                    @Override
                    public void noUpdateAvailable() {
                    System.out.println("No Update Available");
                    }

                    @Override
                    public void onUpdateAvailable() {
                        System.out.println("Update Available");
                    }
                });
           }
       });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
