package ba.unsa.etf.logit;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ba.unsa.etf.logit.model.Attendance;

/**
 * Created by koljenovic on 5/30/17.
 */

public class AttendanceAdapter extends ArrayAdapter<Attendance> {
    private final Context context;
    private final Attendance[] values;

    public AttendanceAdapter(Context context, Attendance[] values) {
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

        pName.setText(this.values[position].getFullName());
        pMail.setText(this.values[position].getMail());

        pDate.setText(this.values[position].getDateString());

        pSeq.setText(Integer.toString(this.values.length - position));
        short valid = this.values[position].getValid();
        String msg = valid > 0 ? "Dobar potpis" : (valid < 0 ? "Loš potpis" : "");
        pMisc.setText(msg);
        return rowView;
    }
}
