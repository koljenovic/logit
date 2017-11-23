package ba.unsa.etf.logit;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NfcAdapter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ba.unsa.etf.logit.api.LogitService;
import ba.unsa.etf.logit.model.Attendance;
import ba.unsa.etf.logit.model.Place;
import ba.unsa.etf.logit.model.Session;
import ba.unsa.etf.logit.model.User;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class AttendanceActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks {

    public static final String MIME = "application/octet-stream";
    public static final String TAG = "Logit";
    private AttendanceActivity that = this;

    private ListView listview;
    private List<Attendance> attns = new ArrayList<Attendance>();

    private GoogleApiClient mGoogleApiClient;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        Toolbar topToolbar = (Toolbar) findViewById(R.id.top_toolbar);
        setSupportActionBar(topToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        SharedPreferences userData = getSharedPreferences("UserData", 0);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this).addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

        refreshLocation();

        topToolbar.setTitle(userData.getString("surname", "Unknown") + ", " + userData.getString("name", "Unknown"));
        topToolbar.setSubtitle(userData.getString("user", "unknown") + "@etf.unsa.ba");

        listview = (ListView) findViewById(R.id.prisutni);

        // Session ID control sequence
        if(!userData.contains("sid")) {
            SharedPreferences.Editor editor = userData.edit();
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte [] sidPayload = (userData.getString("user", "unknown") + System.currentTimeMillis()).getBytes();
                md.update(sidPayload, 0, sidPayload.length);
                editor.putString("sid", LogitApplication.toHext(md.digest()));
                editor.apply();
            } catch (Exception e) {
                Toast.makeText(that, "Greška: neispravan CRYPT zahtjev.", Toast.LENGTH_LONG).show();
            }
        }

        if(!userData.contains("attns")) {
            SharedPreferences.Editor editor = userData.edit();
            editor.putStringSet("attns", Collections.synchronizedSet(new HashSet<String>()));
            editor.apply();
        } else {
            HashSet<String> attnSet = (HashSet<String>) userData.getStringSet("attns", Collections.synchronizedSet(new HashSet<String>()));
            for (String s : attnSet) {
                attns.add(0, new Attendance(s));
            }
            if (!attns.isEmpty()) {
                Attendance[] attnsArray = (new Attendance[attns.size()]);
                attns.toArray(attnsArray);

                final ArrayAdapter adapter = new AttendanceAdapter(that, attnsArray);
                listview.setAdapter(adapter);
            }
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            // Stop here, we need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;

        }

        if (!mNfcAdapter.isEnabled()) {
            // @TODO
        } else {

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupForegroundDispatch(this, mNfcAdapter);
    }

    @Override
    protected void onPause() {
        stopForegroundDispatch(this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            String type = intent.getType();
            if (MIME.equals(type)) {

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);

            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        }
    }

    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                    return readText(ndefRecord);
                }
            }

            return null;
        }

        private String readHext(NdefRecord record) {
            byte[] data = record.getPayload();
            return LogitApplication.toHext(data);
        }

        private String readText(NdefRecord record) {
            byte[] data = record.getPayload();
            String ret;
            try {
                ret = new String(data, "UTF-8");
            } catch (Exception e) {
                ret = "Error";
                e.printStackTrace();
            }
            return ret;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    final Attendance tmpAttn = new Attendance(result);
                    if (tmpAttn.isValidBasic()) {
                        for (Attendance a : attns) {
                            if (tmpAttn.getUid().equals(a.getUid()) || tmpAttn.getUser().equals(a.getUser())) {
//                            if (tmpAttn.getUid().equals(a.getUid())) {
                                Toast.makeText(that, "Student potpisan.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        final long timediff = System.currentTimeMillis() / 1000 - Long.parseLong(tmpAttn.getTs());
                        final Location userLocation = new Location("MOCK");
                        userLocation.setLatitude(Double.valueOf(tmpAttn.getLat()));
                        userLocation.setLongitude(Double.valueOf(tmpAttn.getLon()));
                        if (Build.VERSION.SDK_INT >= 23
                                && ContextCompat.checkSelfPermission(that, android.Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED
                                && ContextCompat.checkSelfPermission(that, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                || Build.VERSION.SDK_INT < 23) {
                            FusedLocationProviderClient mFusedLocatiionClient = LocationServices.getFusedLocationProviderClient(that);
                            mFusedLocatiionClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                                @Override
                                public void onSuccess(Location location) {
                                    Float locdiff = userLocation.distanceTo(location);
//                                    if (Math.abs(timediff) < 300) {
                                    if (true) {
//                                        if (locdiff < 100) {
                                        if (true) {
                                            SharedPreferences userData = getSharedPreferences("UserData", 0);
                                            SharedPreferences.Editor editor = userData.edit();
                                            HashSet<String> attnSet = new HashSet<String>((HashSet<String>) userData.getStringSet("attns", Collections.synchronizedSet(new HashSet<String>())));
                                            attnSet.add(tmpAttn.getRaw());
                                            editor.putStringSet("attns", attnSet);
                                            editor.apply();

                                            attns.add(0, tmpAttn);

                                            Attendance[] attnsArray = (new Attendance[attns.size()]);
                                            attns.toArray(attnsArray);

                                            final ArrayAdapter adapter = new AttendanceAdapter(that, attnsArray);
                                            listview.setAdapter(adapter);
                                        } else {
                                            Toast.makeText(that, "Greška: lokacije udaljene " + locdiff.intValue() + " metara.", Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        Toast.makeText(that, "Greška: vrijeme nije tačno ili je TAG zastario.", Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        } else {
                            Toast.makeText(that, "Greška: lokacija nije dostupna.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    } else {
                        Toast.makeText(that, "Greška: TAG nije valjan.", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in the manifest
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);

        try {
            filters[0].addDataType(MIME);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setFastestInterval(10000);
        mLocationRequest.setNumUpdates(3);
        mLocationRequest.setSmallestDisplacement(1);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT < 23) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Log.d("LOCATION", Double.toString(location.getLatitude()));
                }
            });
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void onSyncButton(View v) {
        if (attns.size() > 0) {
            final ProgressBar validateProgress = (ProgressBar) findViewById(R.id.validate_progress);
            validateProgress.setVisibility(View.VISIBLE);
            final SharedPreferences userData = getSharedPreferences("UserData", 0);
            final SharedPreferences.Editor editor = userData.edit();

            try {
                final MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] confSig;

                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);
                KeyStore.ProtectionParameter pp = new KeyStore.PasswordProtection(null);

                // Get the most recent master secure entry element
                Enumeration<String> aliases = ks.aliases();
                String alias = aliases.nextElement();
                KeyStore.Entry entry = ks.getEntry(alias, pp);

                // Read in the master certificate
                Certificate c = ks.getCertificate(alias);

                // Instantiate a signature object and obtain the private key
                Signature s = Signature.getInstance("SHA256withRSA");
                s.initSign(((KeyStore.PrivateKeyEntry) entry).getPrivateKey());

                // Generate a hash for each attendance signature to be used as unique ID
                ArrayList<String> cidHashes = new ArrayList(attns.size());
                for (int i = 0; i < attns.size(); i++) {
                    attns.get(i).setMaster(userData.getString("user", "unknown"));
                    attns.get(i).setMid(userData.getString("uid", "unknown"));
                    md.update(attns.get(i).getSig().getBytes());
                    attns.get(i).setAid(LogitApplication.toHext(md.digest()));
                    attns.get(i).setSid(userData.getString("sid", "unknown"));

                    // Sign the confsig logit confirmation package
                    s.update(attns.get(i).getConfSigPkg().getBytes());
                    confSig = s.sign();

                    attns.get(i).setConfsig(LogitApplication.toHext(confSig));
                    md.update(confSig);
                    attns.get(i).setCid(LogitApplication.toHext(md.digest()));
                    cidHashes.add(attns.get(i).getCid());
                }
                Collections.sort(cidHashes);

                StringBuilder builder = new StringBuilder();
                for (String cidHash : cidHashes) {
                    builder.append(cidHash);
                }
                String hashPackage = builder.toString();
                s.update(hashPackage.getBytes());
                byte [] rootSignature = s.sign();
                String rootHash = LogitApplication.toHext(rootSignature);

                Session session = new Session();
                session.setSid(userData.getString("sid", "unknown"));
                session.setSig(LogitApplication.toHext(rootSignature));
                session.setMid(userData.getString("uid", "unknown"));
                session.setMaster(userData.getString("user", "unknown"));
                session.setAttns(attns);

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(LogitApplication.SERVICE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                final LogitService service = retrofit.create(LogitService.class);

                Call<ResponseBody> sync = service.sync(session);
                sync.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.code() == 201) {
                            // Reset the session ID and clear the previous attendances list
                            byte[] sidPayload = (userData.getString("user", "unknown") + System.currentTimeMillis()).getBytes();
                            md.update(sidPayload, 0, sidPayload.length);
                            editor.putString("sid", LogitApplication.toHext(md.digest()));
                            editor.putStringSet("attns", Collections.synchronizedSet(new HashSet<String>()));
                            editor.apply();
                            attns.clear();
                            listview.setAdapter(null);
                            Toast.makeText(that, "Podaci uspješno pohranjeni.", Toast.LENGTH_LONG).show();
                        } else if (response.code() == 401) {
                            byte[] sidPayload = (userData.getString("user", "unknown") + System.currentTimeMillis()).getBytes();
                            md.update(sidPayload, 0, sidPayload.length);
                            editor.putString("sid", LogitApplication.toHext(md.digest()));
                            editor.putStringSet("attns", Collections.synchronizedSet(new HashSet<String>()));
                            editor.apply();
                            attns.clear();
                            listview.setAdapter(null);
                            Toast.makeText(that, "Greška: loš potpis sesije.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(that, "Greška: neuspješan Logit zahtjev.", Toast.LENGTH_LONG).show();
                        }
                        validateProgress.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(that, "Greška: Logit servis nedostupan.", Toast.LENGTH_LONG).show();
                        validateProgress.setVisibility(View.INVISIBLE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(that, "Greška: neispravan CRYPT zahtjev.", Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void refreshLocation() {
        final ProgressBar validateProgress = (ProgressBar) findViewById(R.id.validate_progress);
        validateProgress.setVisibility(View.VISIBLE);

        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://nominatim.openstreetmap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        final LogitService service = retrofit.create(LogitService.class);

        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT < 23) {

            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(final Location location) {
                    if (location != null) {
                        Call<Place> validate = service.getAddress("mkoljenovic1@etf.unsa.ba", "json", location.getLatitude(), location.getLongitude(), 18, 0);
                        validate.enqueue(new Callback<Place>() {
                            @Override
                            public void onResponse(Call<Place> call, Response<Place> response) {
                                if (response.code() == 200) {
                                    Place p =  response.body();
                                    String [] address = p.getDisplayName().split(", ");
                                    TextView geoText = (TextView) findViewById(R.id.geoText);
                                    if (address.length > 0) {
                                        geoText.setText(address[0] + " (" + location.getLatitude() + ", " + location.getLongitude() + ")");
                                    }
                                    if (location.getTime() - System.currentTimeMillis() > 600000) {
                                        geoText.setBackgroundResource(android.R.color.holo_orange_light);
                                    }
                                } else {
                                    Toast.makeText(that, "Greška: neispravan OSM zahtjev.", Toast.LENGTH_LONG).show();
                                }
                                validateProgress.setVisibility(View.INVISIBLE);
                            }

                            @Override
                            public void onFailure(Call<Place> call, Throwable t) {
                                Toast.makeText(that, "Greška: OSM servis nedostupan.", Toast.LENGTH_LONG).show();
                                validateProgress.setVisibility(View.INVISIBLE);
                            }
                        });
                    } else {
                        Toast.makeText(that, "Greška: lokacija nije dostupna.", Toast.LENGTH_LONG).show();
                        validateProgress.setVisibility(View.INVISIBLE);
                    }
                }
            });
        }
    }

    public void onValidateButton(View v) {
        final ProgressBar validateProgress = (ProgressBar) findViewById(R.id.validate_progress);
        validateProgress.setVisibility(View.VISIBLE);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(LogitApplication.SERVICE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        LogitService service = retrofit.create(LogitService.class);
        ArrayList<String> attnsRaw = new ArrayList<String>(attns.size());
        for (Attendance attn : attns) {
            attnsRaw.add(attn.getRaw());
        }
        Call<List<Attendance>> validate = service.validate(attnsRaw);
        validate.enqueue(new Callback<List<Attendance>>() {
            @Override
            public void onResponse(Call<List<Attendance>> call, Response<List<Attendance>> response) {
                if (response.code() == 200) {
                    attns.clear();
                    for(Attendance a : response.body()) {
                        attns.add(a);
                    }
                    Attendance[] attnsArray = (new Attendance[attns.size()]);
                    attns.toArray(attnsArray);

                    final ArrayAdapter adapter = new AttendanceAdapter(that, attnsArray);
                    listview.setAdapter(adapter);
                } else {
                    Toast.makeText(that, "Greška: neispravan Logit zahtjev.", Toast.LENGTH_LONG).show();
                }
                validateProgress.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onFailure(Call<List<Attendance>> call, Throwable t) {
                Log.d("validate", "error");
                Toast.makeText(that, "Greška: Logit servis nedosupan.", Toast.LENGTH_LONG).show();
                validateProgress.setVisibility(View.INVISIBLE);
            }
        });
    }

    public void onBugButton(View v) {
        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        SharedPreferences userData = getSharedPreferences("UserData", 0);

        emailIntent.setType("plain/text");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"mkoljenovic1@etf.unsa.ba"});
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Logit bug @" + userData.getString("user", "unknows") + ":" + userData.getString("uid", "unknown"));

        this.startActivity(Intent.createChooser(emailIntent, "Prijavite grešku putem e-maila ..."));
    }

    public void onGeoButton(View v) {
        refreshLocation();
    }
}