package ba.unsa.etf.logit;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import ba.unsa.etf.logit.api.LogitService;
import ba.unsa.etf.logit.model.User;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1337);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        SharedPreferences userData = getSharedPreferences("UserData", 0);

        if(userData.contains("uid")) {
            Intent attnIntent = new Intent(this, AttendanceActivity.class);
            startActivity(attnIntent);
        }
    }

    public void onNewKeyButton(View v) {
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        Long tsLong = System.currentTimeMillis() / 1000;
        String ts = tsLong.toString();
        String certDer = null;
        String pubKeyHashString = null;
        final MainActivity that = this;

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    "RSA", "AndroidKeyStore");
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 1);

            KeyPairGeneratorSpec spec =
                    new KeyPairGeneratorSpec.Builder(this).setAlias("etf_logit_" + ts)
                            .setKeySize(2048)
                            .setSubject(new X500Principal("CN=users.etf.ba"))
                            .setSerialNumber(BigInteger.valueOf(tsLong))
                            .setStartDate(start.getTime()).setEndDate(end.getTime()).build();

            kpg.initialize(spec);

            // Ref: Android Security Internals: An In-Depth Guide to Android's Security Architecture By Nikolay Elenkov

            KeyPair kp = kpg.generateKeyPair();

            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);

//            List<String> aliasesList = Collections.list(aliases);
//            ListView existingCredList = (ListView) findViewById(R.id.existingCredList);
//            existingCredList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, aliasesList));

            // Get the most recent user secure entry element
            Enumeration<String> aliases = ks.aliases();
            String alias = aliases.nextElement();
            KeyStore.ProtectionParameter pp = new KeyStore.PasswordProtection(null);
            KeyStore.Entry entry = ks.getEntry(alias, pp);

            // Instantiate a digest object for hashing
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            // Read in the user certificate
            Certificate c = ks.getCertificate(alias);

            // Generate public key hash as user identifier
            byte [] pubKey = c.getPublicKey().getEncoded();
            md.update(pubKey, 0, pubKey.length);
            byte [] pubKeyHash = md.digest();
            pubKeyHashString = LogitApplication.toHext(pubKeyHash);

            certDer = LogitApplication.toHext(c.getEncoded());
            Log.d("DER", certDer);
        } catch (Exception e) {
            Log.d("logit", Log.getStackTraceString(e));
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(LogitApplication.SERVICE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final EditText usernameBox = (EditText) findViewById(R.id.usernameBox);
        final String usernameValue = usernameBox.getText().toString();
        EditText passwordBox = (EditText) findViewById(R.id.passwordBox);
        String passwordValue = passwordBox.getText().toString();
        final String uid = pubKeyHashString;

        LogitService service = retrofit.create(LogitService.class);
        Call<User> auth = service.auth(usernameValue, passwordValue, certDer, uid);
        auth.enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.code() == 200) {
                    User user = response.body();

                    SharedPreferences userData = getSharedPreferences("UserData", 0);
                    SharedPreferences.Editor editor = userData.edit();
                    editor.putString("uid", uid);
                    editor.putString("user", usernameValue);
                    editor.putString("name", user.getName());
                    editor.putString("surname", user.getSurname());
                    editor.apply();

                    Intent attnIntent = new Intent(that, AttendanceActivity.class);
                    startActivity(attnIntent);
                } else {
                    try {
                        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                        ks.load(null);
                        Enumeration<String> aliases = ks.aliases();
                        List<String> aliasesList = Collections.list(aliases);
                        for (String a : aliasesList) {
                            ks.deleteEntry(a);
                        }
                        Toast.makeText(that, "Došlo je do greške, pokušajte ponovo.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                }
                progressBar.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                Log.d("RESP", "err");
                Toast.makeText(that, "Došlo je do greške, pokušajte ponovo.", Toast.LENGTH_LONG).show();
            }
        });
    }

}
