package ba.unsa.etf.logit;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Enumeration;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.cardemulation.HostApduService;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;


public class LogitApduService extends HostApduService {

    final static int APDU_INS = 1;
    final static int APDU_P1 = 2;
    final static int APDU_P2 = 3;
    final static int APDU_SELECT_LC = 4;
    final static int APDU_READ_LE = 4;
    final static int FILEID_CC = 0xe103;
    final static int FILEID_NDEF = 0xe104;
    final static byte INS_SELECT = (byte) 0xa4;
    final static byte INS_READ = (byte) 0xb0;
    final static byte INS_UPDATE = (byte) 0xd6;
    final static byte P1_SELECT_BY_NAME = (byte) 0x04;
    final static byte P1_SELECT_BY_ID = (byte) 0x00;
    final static int DATA_OFFSET = 5;

    final static byte[] DATA_SELECT_NDEF = {(byte) 0xd2, (byte) 0x76, (byte) 0x00, (byte) 0x00, (byte) 0x85, (byte) 0x01, (byte) 0x01};
    final static byte[] RET_COMPLETE = {(byte) 0x90, (byte) 0x00};
    final static byte[] RET_NONDEF = {(byte) 0x6a, (byte) 0x82};
    final static byte[] FILE_CC = {
            (byte) 0x00, (byte) 0x0f,       // CCLEN - CC container size
            (byte) 0x20,                    // Mapping version
            (byte) 0x04, (byte) 0xff,       // MLe - max. read size
            (byte) 0x08, (byte) 0xff,       // MLc - max. update size

            // TLV Block (NDEF File Control)
            (byte) 0x04,                    // Tag - Block type
            (byte) 0x06,                    // Length
            (byte) 0xe1, (byte) 0x04,       // File identifier
            (byte) 0x04, (byte) 0xff,       // Max. NDEF file size
            (byte) 0x00,                    // R permission
            (byte) 0x00,                    // W permission
    };
    private final static String TAG = "LogitApduService";
    private final static String ALL = "AllLogitApduService";
    private CardSelect mCardSelect = CardSelect.SELECT_NONE;
    private boolean mSelectNdef = false;
    private byte[] mNdefFile = null;
    private LogitApplication logitApp;
    private FusedLocationProviderClient mFusedLocationClient;
    protected String msg;

    public LogitApduService() {
        super();
    }

    private void generateSignature() {
        try {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            // App has to check if the user has granted an explicit permission to use the location
            // This only applies to Android API level 23 and up
            if (Build.VERSION.SDK_INT >= 23
                    && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < 23) {

                mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            try {
                                Long tsLong = System.currentTimeMillis() / 1000;
                                String ts = tsLong.toString();
                                byte[] signature;

                                // Instantiate and load a Android KeyStore object
                                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                                ks.load(null);
                                KeyStore.ProtectionParameter pp = new KeyStore.PasswordProtection(null);

                                // Get the most recent user secure entry element
                                Enumeration<String> aliases = ks.aliases();
                                String alias = aliases.nextElement();
                                Entry entry = ks.getEntry(alias, pp);

                                // Instantiate a digest object for hashing
                                MessageDigest md = MessageDigest.getInstance("SHA-256");

                                // Read in the user certificate
                                Certificate c = ks.getCertificate(alias);

                                // Generate public key hash as user identifier
                                byte [] pubKey = c.getPublicKey().getEncoded();
                                md.update(pubKey, 0, pubKey.length);
                                byte [] pubKeyHash = md.digest();
                                String pubKeyHashString = LogitApplication.toHext(pubKeyHash);

                                // Instantiate a signature object and obtain the private key
                                Signature s = Signature.getInstance("SHA256withRSA");
                                s.initSign(((PrivateKeyEntry) entry).getPrivateKey());

                                SharedPreferences userData = getSharedPreferences("UserData", 0);

                                // Prepare the logit data package to be signed
                                String sigPkg = userData.getString("user", "unknown") +
                                        ":" + location.getLatitude() +
                                        ":" + location.getLongitude() +
                                        ":" + ts;

                                // Sign the logit data package
                                s.update(sigPkg.getBytes("UTF-8"));
                                signature = s.sign();

                                // Generate a tx package to be sent to master
                                msg = "{\"lat\":\"" + location.getLatitude() +
                                        "\", \"lon\":\"" + location.getLongitude() +
                                        "\", \"ts\":\"" + ts +
                                        "\", \"sig\":\"" + LogitApplication.toHext(signature) +
                                        "\", \"uid\":\"" + pubKeyHashString +
                                        "\", \"name\":\"" + LogitApplication.toHext(userData.getString("name", "unknown").getBytes("UTF-8")) +
                                        "\", \"surname\":\"" + LogitApplication.toHext(userData.getString("surname", "unknown").getBytes("UTF-8")) +
                                        "\", \"user\":\"" + userData.getString("user", "unknown") + "\"}";

                                // Create a NDEF message from the tx package
                                NdefMessage ndef = createMessage(msg.getBytes("UTF-8"));
                                byte[] ndefarray = ndef.toByteArray();

                                // Prepare a NDEF file for HCE Tag emulation
                                mNdefFile = new byte[ndefarray.length + 2];

                                // Append length bytes as per NDEF NDFILE specification
                                mNdefFile[0] = (byte) ((ndefarray.length & 0xff00) >> 8);
                                mNdefFile[1] = (byte) (ndefarray.length & 0x00ff);

                                // Copy the NDEF message into the NDEF file
                                System.arraycopy(ndefarray, 0, mNdefFile, 2, ndefarray.length);

                                logitApp.setMessage(mNdefFile);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "onDeactivated");
        mCardSelect = CardSelect.SELECT_NONE;
        mSelectNdef = false;
    }

    protected byte [] prepareRetData(byte [] commandApdu) {
        return prepareRetData(commandApdu, null);
    }

    protected byte [] prepareRetData(byte [] commandApdu, byte [] src) {
        if (src == null) {
            Log.d(TAG, "return complete");
            return RET_COMPLETE;
        }

        int offset = ((commandApdu[APDU_P1]) << 8) | commandApdu[APDU_P2];
        Log.d(TAG, "offset: " + Integer.toString(offset));
        int Le = commandApdu[APDU_READ_LE] & 0xff;
        byte [] retData = new byte[Le + RET_COMPLETE.length];

        // Copy payload data into R-APDU
        System.arraycopy(src, offset, retData, 0, Le);
        // Add terminator to R-APDU
        System.arraycopy(RET_COMPLETE, 0, retData, Le, RET_COMPLETE.length);

        Log.d(TAG, "******************************");
        for (byte ch : retData) {
            Log.d(TAG, Integer.toHexString(ch & 0xff));
        }
        Log.d(TAG, "******************************");

        return retData;
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        for (int i = 0; i < commandApdu.length; i++) {
            Log.d(ALL, Integer.toHexString(commandApdu[i] & 0xff));
        }

        byte [] retData = RET_NONDEF;

        switch (commandApdu[APDU_INS]) {
            case INS_SELECT:

                switch (commandApdu[APDU_P1]) {
                    case P1_SELECT_BY_NAME:
                        Log.d(TAG, "select : name");
                        // 1. NDEF Tag Application Select
                        if (memCmp(commandApdu, DATA_OFFSET, DATA_SELECT_NDEF, 0, commandApdu[APDU_SELECT_LC])) {
                            //select NDEF application
                            Log.d(TAG, "select NDEF application");
                            mSelectNdef = true;
                            retData = prepareRetData(commandApdu);
                        } else {
                            Log.e(TAG, "select: fail");
                        }
                        break;

                    case P1_SELECT_BY_ID:
                        Log.d(TAG, "select : id");
                        if (mSelectNdef) {
                            int file_id = 0;
                            for (int loop = 0; loop < commandApdu[APDU_SELECT_LC]; loop++) {
                                file_id <<= 8;
                                file_id |= commandApdu[DATA_OFFSET + loop] & 0xff;
                            }
                            switch (file_id) {
                                case FILEID_CC:
                                    Log.d(TAG, "select CC file");
                                    mCardSelect = CardSelect.SELECT_CCFILE;
                                    retData = prepareRetData(commandApdu);
                                    break;

                                case FILEID_NDEF:
                                    Log.d(TAG, "select NDEF file");
                                    mCardSelect = CardSelect.SELECT_NDEFFILE;
                                    retData = prepareRetData(commandApdu);
                                    break;

                                default:
                                    Log.e(TAG, "select: unknown file id : " + file_id);
                                    break;
                            }
                        } else {
                            Log.e(TAG, "select: not select NDEF app");
                        }
                        break;

                    default:
                        Log.e(TAG, "select: unknown p1 : " + commandApdu[APDU_P1]);
                        break;
                }
                break;

            case INS_READ:
                Log.d(TAG, "read");
                if (mSelectNdef) {
                    byte[] src = null;
                    switch (mCardSelect) {
                        case SELECT_CCFILE:
                            Log.d(TAG, "read cc file");
                            retData = prepareRetData(commandApdu, FILE_CC);
                            break;

                        case SELECT_NDEFFILE:
                            Log.d(TAG, "read ndef file");
                            retData = prepareRetData(commandApdu, logitApp.getMessage());
                            break;
                    }
                } else {
                    Log.e(TAG, "read: not select NDEF app");
                }
                break;

            case INS_UPDATE:
                Log.d(TAG, "UPDATE not implemented");

            default:
                Log.e(TAG, "unknown INS : " + commandApdu[APDU_INS]);
                break;
        }

        if (retData == RET_NONDEF) {
            Log.d(TAG, "ret notdef");
        }

        return retData;
    }

    private boolean memCmp(final byte[] p1, int offset1, final byte[] p2, int offset2, int cmpLen) {
        final int len = p1.length;
        if ((len < offset1 + cmpLen) || (p2.length < offset2 + cmpLen)) {
            Log.d(TAG, "memCmp fail : " + offset1 + " : " + offset2 + " (" + cmpLen + ")");
            Log.d(TAG, "memCmp fail : " + len + " : " + p2.length);
            return false;
        }

        boolean ret = true;
        for (int loop = 0; loop < cmpLen; loop++) {
            if (p1[offset1 + loop] != p2[offset2 + loop]) {
                Log.d(TAG, "unmatch");
                ret = false;
                break;
            }
        }

        return ret;
    }

    //https://github.com/bs-nfc/WriteRTDUri/blob/master/src/jp/co/brilliantservice/android/writertduri/HomeActivity.java
    private NdefMessage createUriMessage(int index, String uriBody) {
        try {
            byte[] uriBodyBytes = uriBody.getBytes("UTF-8");
            byte[] payload = new byte[1 + uriBody.length()];
            payload[0] = (byte) index;
            System.arraycopy(uriBodyBytes, 0, payload, 1, uriBodyBytes.length);
            return new NdefMessage(new NdefRecord[]{
                    new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload)
            });
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private NdefMessage createMessage(byte [] body) {
        try {
            NdefRecord r0 = NdefRecord.createMime("application/octet-stream", body);
            return new NdefMessage(r0);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logitApp = ((LogitApplication) this.getApplication());
        generateSignature();
    }

    enum CardSelect {
        SELECT_NONE,
        SELECT_CCFILE,
        SELECT_NDEFFILE,
    }
}
