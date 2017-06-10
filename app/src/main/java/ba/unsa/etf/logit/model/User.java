package ba.unsa.etf.logit.model;

/**
 * Created by koljenovic on 5/30/17.
 */

public class User {
    public String name;

    public User(String name, String mail) {
        this.name = name;
        this.mail = mail;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String mail;

    @Override
    public String toString() {
        return this.getName() + " (" + this.getMail() + ")";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
