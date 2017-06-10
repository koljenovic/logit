package ba.unsa.etf.logit;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.menu.ExpandedMenuView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

import javax.security.auth.x500.X500Principal;

import ba.unsa.etf.logit.model.User;

import static android.R.attr.end;

public class MainActivity extends AppCompatActivity {

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    public void listKeys() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
        } catch (Exception e) {

        }

//        ArrayList keyAliases = new ArrayList<>();
//
//        try {
//            Enumeration<String> aliases = keyStore.aliases();
//            while (aliases.hasMoreElements()) {
//                keyAliases.add(aliases.nextElement());
//            }
//        }
//        catch(Exception e) {}
    }

    public void generateKeypair(String alias) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(this)
                    .setAlias(alias)
                    .setSubject(new X500Principal("CN=mkoljenovic1, O=etf.unsa.ba"))
                    .setSerialNumber(BigInteger.ONE)
                    .setKeySize(4096)
                    .setStartDate(new Date())
                    .setEndDate(new Date(1748619712))
                    .build();
            kpg.initialize(spec);
            KeyPair kp = kpg.generateKeyPair();
        } catch (Exception e) {
            Toast.makeText(this, "Exception " + e.getMessage() + " occured", Toast.LENGTH_LONG).show();
            Log.e("ERROR", Log.getStackTraceString(e));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar topToolbar = (Toolbar) findViewById(R.id.top_toolbar);
        setSupportActionBar(topToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        User u = new User("Malik Koljenović", "mkoljenovic1@etf.unsa.ba");
        topToolbar.setTitle(u.getName());
        topToolbar.setSubtitle(u.getMail());

        SharedPreferences wmbPreference = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFirstRun = wmbPreference.getBoolean("FIRSTRUN", true);
        if (isFirstRun) {
            SharedPreferences.Editor editor = wmbPreference.edit();
            editor.putBoolean("FIRSTRUN", false);
            editor.commit();

            this.generateKeypair("test-0000");
        }


        final ListView listview = (ListView) findViewById(R.id.prisutni);
        String[] values = new String[] { "Android", "iPhone", "WindowsMobile",
                "Blackberry", "WebOS", "Ubuntu", "Windows7", "Max OS X",
                "Linux", "OS/2", "Ubuntu", "Windows7", "Max OS X", "Linux",
                "OS/2", "Ubuntu", "Windows7", "Max OS X", "Linux", "OS/2",
                "Android", "iPhone", "WindowsMobile" };

        User[] users = new User[] {new User("Adna Duraković", "adurakovic1@etf.unsa.ba"),
                new User("Haris Šemić", "hsemic4@etf.unsa.ba"),
                new User("Hamdija Sinanović", "hsinanovic1@etf.unsa.ba"),
                new User("Haris Šemić", "hsemic4@etf.unsa.ba"),
                new User("Hamdija Sinanović", "hsinanovic1@etf.unsa.ba"),
                new User("Haris Šemić", "hsemic4@etf.unsa.ba"),
                new User("Hamdija Sinanović", "hsinanovic1@etf.unsa.ba"),
                new User("Haris Šemić", "hsemic4@etf.unsa.ba"),
                new User("Hamdija Sinanović", "hsinanovic1@etf.unsa.ba")
        };

        final ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < values.length; ++i) {
            list.add(values[i]);
        }

        final ArrayAdapter adapter = new PrisutniAdapter(this, users);
        listview.setAdapter(adapter);

    }
}
