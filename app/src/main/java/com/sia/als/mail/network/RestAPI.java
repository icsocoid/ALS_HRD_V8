package com.sia.als.mail.network;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.sia.als.R;
import com.sia.als.mail.database.EmailMessage;
import com.sia.als.mail.database.User;
import com.sia.als.mail.utils.BasePath;
import com.sia.als.mail.utils.Constants;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * Created by rish on 7/1/16.
 */
public class RestAPI {

    private static final String TAG = "RESTAPI";

    private User user;
    private Context context;
    private ArrayList<EmailMessage> allNewEmails = new ArrayList<>();
    private OkHttpClient okHttpClient;

    public RestAPI(User user, Context context) {
        this.user = user;
        this.context = context;
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean logIn() {
        return makeLoginRequest();
    }

    public boolean refresh(String folder) {
        return handleRefreshAndLoadMoreRequest(folder, Constants.REFRESH_TYPE_REFRESH);
    }

    public boolean loadMore(String folder, int lengthToLoad) {
        return handleRefreshAndLoadMoreRequest(folder, Constants.REFRESH_TYPE_LOAD_MORE, lengthToLoad);
    }

    public EmailMessage fetchEmailContent(EmailMessage emailMessage) {
        return makeFetchContentRequest(emailMessage);
    }

    private String getAuthHeader() {
        return okhttp3.Credentials.basic(user.getUsername(), user.getPassword());
    }

    private boolean makeLoginRequest() {
        try {
            Request request = new Request.Builder()
                    .url(context.getString(R.string.rest_url_login))
                    .header("Authorization", getAuthHeader())
                    .build();

            Response response = okHttpClient.newCall(request).execute();

            return response.isSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean handleRefreshAndLoadMoreRequest(String folder, String refreshType, int lengthToLoad) {
        allNewEmails = new ArrayList<>();

        if (folder.equals(Constants.INBOX)) {
            /**
             * Traverse through all stored emails, and delete those that aren't there in fetchedList
             * Also update those that have changed read/unread status
             */
            ArrayList<EmailMessage> fetchedEmails = fetchMailsOfFolder(folder);

            if (fetchedEmails == null)
                return false;

            for (EmailMessage storedEmail : EmailMessage.getAllMailsOfUser(user)) {
                boolean storedEmailFound = false;
                for (EmailMessage fetchedEmail : fetchedEmails) {
                    if (fetchedEmail.getContentID() == storedEmail.getContentID()) {
                        storedEmailFound = true;
                        if (!fetchedEmail.getReadUnread().equals(storedEmail.getReadUnread())) {
                            EmailMessage.updateReadStatus(storedEmail, fetchedEmail.getReadUnread());
                        }
                    }
                }
                if (!storedEmailFound) {
                    EmailMessage.deleteEmailMessage(storedEmail);
                }
            }

            EmailMessage lastWebmail = EmailMessage.getLastWebmailOfUser(user);
            EmailMessage latestWebmail = EmailMessage.getLatestWebmailOfUser(user);

            int indexOfLastEmailInFetchedList = 0;
            int indexOfLatestEmailInFetchedList = 0;

            /**
             * Find index of latest and last webmails in the fetched list
             * All emails above latestEmails are ones to be saved in refresh
             * lengthToLoad emails below lastEmail are ones to be saved in loadmore
             */
            for (int i = 0; i < fetchedEmails.size(); i++) {
                if (lastWebmail != null && fetchedEmails.get(i).getContentID() == lastWebmail.getContentID())
                    indexOfLastEmailInFetchedList = i;
                if (latestWebmail != null && fetchedEmails.get(i).getContentID() == latestWebmail.getContentID())
                    indexOfLatestEmailInFetchedList = i;
            }

            /**
             * Two cases : Refresh or Load More
             */
            if (refreshType.equals(Constants.REFRESH_TYPE_REFRESH)) {
                for (int m = 0; m < indexOfLatestEmailInFetchedList; m++) {
                    EmailMessage fetchedEmail = fetchedEmails.get(m);
                    EmailMessage emailMessage = EmailMessage.saveNewEmailMessage(user, fetchedEmail.getContentID(), fetchedEmail.getFromName(), fetchedEmail.getFromAddress(), fetchedEmail.getSubject(), fetchedEmail.getDateInMillis(), fetchedEmail.getReadUnread(), fetchedEmail.getTotalAttachments(), fetchedEmail.isImportant());
                    allNewEmails.add(emailMessage);
                }
            } else if (refreshType.equals(Constants.REFRESH_TYPE_LOAD_MORE)) {
                /* Check if fetchedEmailSize is big enough to load lengthToLoad */
                lengthToLoad = (lengthToLoad + indexOfLastEmailInFetchedList) <= (fetchedEmails.size()) ? (lengthToLoad) : (fetchedEmails.size() - indexOfLastEmailInFetchedList);
                for (int m = indexOfLastEmailInFetchedList; m < indexOfLastEmailInFetchedList + lengthToLoad; m++) {
                    EmailMessage fetchedEmail = fetchedEmails.get(m);
                    EmailMessage emailMessage = EmailMessage.saveNewEmailMessage(user, fetchedEmail.getContentID(), fetchedEmail.getFromName(), fetchedEmail.getFromAddress(), fetchedEmail.getSubject(), fetchedEmail.getDateInMillis(), fetchedEmail.getReadUnread(), fetchedEmail.getTotalAttachments(), fetchedEmail.isImportant());
                    allNewEmails.add(emailMessage);
                }
            }
        } else {
            ArrayList allEmailsOfFolder = fetchMailsOfFolder(folder);
            if (allEmailsOfFolder != null)
                allNewEmails.addAll(allEmailsOfFolder);
        }
        return false;
    }

    private boolean handleRefreshAndLoadMoreRequest(String folder, String refreshType) {
        return handleRefreshAndLoadMoreRequest(folder, refreshType, Integer.MAX_VALUE);
    }

    private ArrayList<EmailMessage> fetchMailsOfFolder(String folder) {
        ArrayList<EmailMessage> parsedMails = null;
        URL url = null;
        try {
            if (folder.equals(Constants.INBOX))
                url = new URL(context.getString(R.string.rest_url_inbox));
            else if (folder.equals(Constants.SENT))
                url = new URL(context.getString(R.string.rest_url_sent));
            else if (folder.equals(Constants.TRASH))
                url = new URL(context.getString(R.string.rest_url_trash));

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", getAuthHeader())
                    .build();

            Response response = okHttpClient.newCall(request).execute();

            if (response.code() == 200) {
                parsedMails = new ArrayList<>();

                JSONObject responseObject = new JSONObject(response.body().string());

                for (int i = 0; i < responseObject.getJSONArray("m").length(); i++) {
                    JSONObject webmailObject = (JSONObject) responseObject.getJSONArray("m").get(i);
                    int contentID = Integer.parseInt(webmailObject.getString("id"));
                    int totalAttachments = 0;
                    String fromName = "fromName";
                    String fromAddress = "fromAddress";
                    String subject = webmailObject.getString("su");
                    String readUnread = Constants.WEBMAIL_READ;
                    boolean important = false;
                    if (webmailObject.has("f")) {
                        if (webmailObject.getString("f").contains("u"))
                            readUnread = Constants.WEBMAIL_UNREAD;
                        if (webmailObject.getString("f").contains("a"))
                            totalAttachments = 1;
                        if (webmailObject.getString("f").contains("!"))
                            important = true;
                        else
                            important = false;
                    }
                    String dateInMillis = webmailObject.getString("d");

                    for (int j = 0; j < webmailObject.getJSONArray("e").length(); j++) {
                        JSONObject fromToObject = (JSONObject) webmailObject.getJSONArray("e").get(j);
                        if (fromToObject.getString("t").equals("f")) {
                            fromAddress = fromToObject.getString("a");
                            if (fromToObject.has("p"))
                                fromName = fromToObject.getString("p");
                            else
                                fromName = fromToObject.getString("d");
                        }
                    }

                    EmailMessage emailMessage = new EmailMessage(user.getUsername(), contentID, fromName, fromAddress, subject, dateInMillis, readUnread, "", totalAttachments, important);
                    parsedMails.add(emailMessage);
                }
                return parsedMails;
            } else {
                return parsedMails;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return parsedMails;
        }
    }

    private EmailMessage makeFetchContentRequest(EmailMessage emailMessage) {
        try {
            /**
             * ToDo : Change this ugly code to OkHTTP
             */
            URL url = new URL(context.getString(R.string.rest_url_view_webmail) + emailMessage.getContentID());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            String userPassword = user.getUsername() + ":" + user.getPassword();
            String encoding = Base64.encodeToString(userPassword.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
            conn.setRequestProperty("Authorization", "Basic " + encoding);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder total = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    total.append(line + "\n");
                }
                in.close();

                writeStringAsFile(context, total.toString());

                MailParser mailParser = new MailParser();
                mailParser.newMailParser(context, emailMessage.getContentID(), total.toString());
                emailMessage.setContent(mailParser.getContentHTML());
                emailMessage.setTotalAttachments(mailParser.getTotalAttachments());

                return emailMessage;
            } else {
                Log.d(TAG, "Resp not successful");
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void writeStringAsFile(Context context, final String fileContents) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(BasePath.getBasePath(context) + "/email.txt");
            OutputStreamWriter out = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
            out.write(fileContents);
            out.close();
        } catch (IOException e) {
        }
    }

    public ArrayList<EmailMessage> getNewEmails() {
        return allNewEmails;
    }
}