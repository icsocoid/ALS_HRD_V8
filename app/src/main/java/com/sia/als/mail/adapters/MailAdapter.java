package com.sia.als.mail.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import androidx.recyclerview.widget.RecyclerView;

import com.sia.als.R;
import com.sia.als.mail.database.EmailMessage;
import com.sia.als.mail.utils.Constants;
import com.sia.als.mail.utils.DateUtils;
import com.sia.als.mail.utils.TheFont;

import java.util.ArrayList;

public class MailAdapter extends RecyclerView.Adapter<MailAdapter.ViewHolder>{

    private final String TAG = "MailAdapter";

    private Context context;
    private LayoutInflater inflater;
    private ArrayList<EmailMessage> emails;
    private ArrayList<EmailMessage> emailsMarkedForAction;
    private boolean clickedForDelete[];
    private String emailType;

    private MultiMailActionSelectedListener multiMailActionSelectedListener;

    public MailAdapter(ArrayList<EmailMessage> emails, Context context, MultiMailActionSelectedListener multiMailActionSelectedListener, String emailType) {
        this.context = context;
        this.emailsMarkedForAction = new ArrayList<>();
        this.emails = emails;
        this.multiMailActionSelectedListener = multiMailActionSelectedListener;
        this.clickedForDelete = new boolean[this.emails.size()];
        this.emailType = emailType;
    }

    @Override
    public MailAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (context != null)
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.element_email, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MailAdapter.ViewHolder holder, final int position) {
        final EmailMessage currentEmail = emails.get(position);

        if (currentEmail.getReadUnread().equals(Constants.WEBMAIL_UNREAD)) {
            holder.msgFrom.setTypeface(null, Typeface.BOLD);
            if (!clickedForDelete[position]) {
                if (currentEmail.getTotalAttachments() >= 1)
                    holder.msgIcon.setImageResource(R.drawable.msg_unread_att);
                else if (currentEmail.isImportant())
                    holder.msgIcon.setImageResource(R.drawable.msg_unread_imp);
                else
                    holder.msgIcon.setImageResource(R.drawable.msg_unread);
            } else {
                holder.msgIcon.setAnimation(AnimationUtils.loadAnimation(context, R.anim.abc_grow_fade_in_from_bottom));
                holder.msgIcon.setImageResource(R.drawable.msg_unread_checked);
            }
        } else {
            holder.msgFrom.setTypeface(null, Typeface.NORMAL);
            if (!clickedForDelete[position]) {
                if (currentEmail.getTotalAttachments() >= 1)
                    holder.msgIcon.setImageResource(R.drawable.msg_read_att);
                else if (currentEmail.isImportant())
                    holder.msgIcon.setImageResource(R.drawable.msg_read_imp);
                else
                    holder.msgIcon.setImageResource(R.drawable.msg_read);
            } else {
                holder.msgIcon.setImageResource(R.drawable.msg_read_checked);
                holder.msgIcon.setAnimation(AnimationUtils.loadAnimation(context, R.anim.abc_grow_fade_in_from_bottom));
            }
        }

        holder.msgFrom.setText(currentEmail.getFromName());
        holder.msgDateRecv.setText(DateUtils.getDate(context, Long.parseLong(currentEmail.getDateInMillis())));
        holder.msgSubject.setText(currentEmail.getSubject());

        holder.msgContainer.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View arg0) {
                addEmailForDelete(position, currentEmail);
                return true;
            }
        });

        holder.msgIcon.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                addEmailForDelete(position, currentEmail);
            }
        });

        holder.msgContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent intent = new Intent(context, ViewEmail.class);
                Bundle bundle = new Bundle();
                /*
                 * Sending email type, and the email.
                 * All operations that change the EmailMessage object must happen there,
                 * and it must return from there, the unsaved object.
                 * It is then our choice as to whether we want to save it or not.
                 * I am not saving SentBox, and TrashBox.
                 */
                bundle.putSerializable(Constants.CURRENT_EMAIL_SERIALIZABLE, currentEmail);
                bundle.putString(Constants.CURRENT_EMAIL_TYPE, emailType);
                if (currentEmail.getId() == null) // isn't a saved object, and hence doesnt have an id
                    bundle.putLong(Constants.CURRENT_EMAIL_ID, -1);
                else
                    bundle.putLong(Constants.CURRENT_EMAIL_ID, emails.get(position).getId());
//                intent.putExtras(bundle);
//                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emails.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView msgIcon;
        TextView msgFrom, msgSubject, msgDateRecv;
        LinearLayout msgContainer;

        public ViewHolder(View view) {
            super(view);
            msgContainer = (LinearLayout) view.findViewById(R.id.element_msg_container);
            msgIcon = (ImageView) view.findViewById(R.id.element_msg_icon);
            msgFrom = (TextView) view.findViewById(R.id.element_msg_from);
            msgDateRecv = (TextView) view.findViewById(R.id.element_msg_date);
            msgSubject = (TextView) view.findViewById(R.id.element_msg_subject);

            Typeface font = TheFont.getFont(context);

            msgDateRecv.setTypeface(font);
            msgFrom.setTypeface(font);
            msgSubject.setTypeface(font);

            view.setTag(this);

        }
    }

    public interface MultiMailActionSelectedListener {
        void onItemClickedForDelete(ArrayList<EmailMessage> emailsMarkedForAction);
    }

    private void addEmailForDelete(int position, EmailMessage item) {
        if (clickedForDelete[position]) {
            clickedForDelete[position] = false;
            notifyDataSetChanged();
            emailsMarkedForAction.remove(item);
        } else {
            clickedForDelete[position] = true;
            emailsMarkedForAction.add(item);
            notifyDataSetChanged();
            Vibrator vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibe.vibrate(20);
        }
        multiMailActionSelectedListener.onItemClickedForDelete(emailsMarkedForAction);
    }

    public void setEmails(ArrayList<EmailMessage> emails) {
        this.emails = emails;
        clickedForDelete = new boolean[this.emails.size()];
    }

    public boolean[] getMarkedMails(){
        return clickedForDelete;
    }

    public void restoreMarkedMails(boolean marked[]){
        for(int i=0; i<emails.size(); i++){
            if(marked[i]){
                addEmailForDelete(i, emails.get(i));
            }
        }
        multiMailActionSelectedListener.onItemClickedForDelete(emailsMarkedForAction);
    }
}