package ba.unsa.etf.logit.model;

import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ba.unsa.etf.logit.LogitApplication;

public class Attendance {
    // Raw JSON version of this object
    public String raw;
    // Attendee name
    public String name;
    public String surname;
    // Attendee latitude
    public String lat;
    // Attendee longitude
    public String lon;
    // Attendee username - zamger
    public String user;
    // Attendee User ID - hex hash of public certificate key
    public String uid;
    // Hex Signature String of attendee package data (lat:lot:ts)
    public String sig;
    // Attendance ID - hex hash of signature
    public String aid;
    // Attendance TimeStamp from attendees device
    public String ts;
    // Is the signature valid check performed remotely by Logit Service on demand
    public short valid;
    // Master username - zamger
    public String master;
    // Master ID - hex hash of masters public certificate key
    public String mid;
    // Attendance Session ID
    public String sid;
    // Master Confirmation Signature - hex signature string of (sid:aid)
    public String confsig;
    // Confirmation ID - hex hash of confsig
    public String cid;

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getConfsig() {
        return confsig;
    }

    public void setConfsig(String confsig) {
        this.confsig = confsig;
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public String getMaster() {
        return master;
    }

    public void setMaster(String master) {
        this.master = master;
    }

    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    public boolean isValidBasic() {
        if (this.getRaw() != null &&
                this.getUser() != null &&
                this.getUid() != null &&
                this.getLat() != null &&
                this.getLon() != null &&
                this.getTs() != null &&
                this.getSig() != null) {
            return true;
        } else {
            return false;
        }
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        this.sig = sig;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public Date getDate() {
        return new Date(Long.parseLong(this.ts) * 1000L);
    }

    public String getDateString() {
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy (HH:mm:ss)");
        return dateFormat.format(this.getDate());
    }

    public Attendance(String raw) {

        try {
            this.raw = raw;
            JSONObject jResult = new JSONObject(raw);

            this.name = new String(LogitApplication.fromHext(jResult.getString("name")), "UTF-8");
            this.surname = new String(LogitApplication.fromHext(jResult.getString("surname")), "UTF-8");
            this.lat = jResult.getString("lat");
            this.lon = jResult.getString("lon");
            this.user = jResult.getString("user");
            this.uid = jResult.getString("uid");
            this.sig = jResult.getString("sig");
            this.ts = jResult.getString("ts");
            this.valid = jResult.has("valid") ? (short)jResult.getInt("valid") : 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public short getValid() {
        return valid;
    }

    public void setValid(short valid) {
        this.valid = valid;
    }

    public String getMail() {
        return this.user + "@etf.unsa.ba";
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return surname + ", " + name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSigPkg() {
        return this.getUser() + ":" + this.getLat() + ":" + this.getLon() + ":" + this.getTs();
    }

    public String getConfSigPkg() {
        return this.getMaster() + ":" + this.getSid() + ":" + this.getAid();
    }
}
