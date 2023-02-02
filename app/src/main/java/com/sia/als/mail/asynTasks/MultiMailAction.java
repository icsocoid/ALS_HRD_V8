package com.sia.als.mail.asynTasks;

import android.content.Context;
import android.os.AsyncTask;

import com.sia.als.mail.database.EmailMessage;
import com.sia.als.mail.database.User;
import com.sia.als.mail.network.SoapAPI;

import java.util.ArrayList;

/**
 * Created by rish on 6/10/15.
 */
public class MultiMailAction extends AsyncTask<Void, Void, Void> {

    private Context context;
    private MultiMailActionListener multiMailActionListener;
    private Boolean result = false;
    private ArrayList<EmailMessage> emailsForMultiAction;
    private User currentUser;
    private String msgAction;

    public MultiMailAction(User user, Context context, MultiMailActionListener multiMailActionListener, ArrayList<EmailMessage> emailsForMultiAction, String msgAction) {
        this.multiMailActionListener = multiMailActionListener;
        this.emailsForMultiAction = emailsForMultiAction;
        this.context = context;
        this.currentUser = user;
        this.msgAction = msgAction;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        multiMailActionListener.onPreMultiMailAction();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        SoapAPI soapAPI = new SoapAPI(context, currentUser);
        /**
         * Traverse through all emailsToBeDeleted
         * If any of them is unsuccessful, return false
         */
        for (EmailMessage emailMessage : emailsForMultiAction) {
            result = soapAPI.performMailAction(msgAction, String.valueOf(emailMessage.getContentID()));
            if (!result)
                return null;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        multiMailActionListener.onPostMultiMailAction(result, msgAction, emailsForMultiAction);
    }
}