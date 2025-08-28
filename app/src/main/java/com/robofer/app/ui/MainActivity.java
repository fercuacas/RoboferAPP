package com.robofer.app.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.robofer.app.R;
import com.robofer.app.features.provision.ProvisionFragment;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, new ProvisionFragment())
                .commitNow();
        }
    }
}
