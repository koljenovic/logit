package ba.unsa.etf.logit;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import ba.unsa.etf.logit.model.User;

/**
 * Created by koljenovic on 5/30/17.
 */

public class PrisutniAdapter extends ArrayAdapter<User> {
    private final Context context;
    private final User[] values;

    public PrisutniAdapter(Context context, User[] values) {
        super(context, R.layout.prisutni_row, values);

        this.context = context;
        this.values = values;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.prisutni_row, parent, false);

        TextView pName = (TextView) rowView.findViewById(R.id.pName);
        TextView pMail = (TextView) rowView.findViewById(R.id.pMail);
        TextView pDate = (TextView) rowView.findViewById(R.id.pDate);
        TextView pSeq = (TextView) rowView.findViewById(R.id.pSeq);
        TextView pMisc  = (TextView) rowView.findViewById(R.id.pMisc);

        pName.setText(this.values[position].getName());
        pMail.setText(this.values[position].getMail());

        Date date = new Date();
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this.context);
        pDate.setText(dateFormat.format(date));

        pSeq.setText(Integer.toString(this.values.length - position));
        pMisc.setText("");
        return rowView;
    }
}
