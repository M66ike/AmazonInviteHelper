package com.mike.invitehelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ShareReceiverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
        finish();
    }

    private void handle(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence raw = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        if (subject == null) subject = intent.getCharSequenceExtra(Intent.EXTRA_TITLE);

        boolean added = QueueStore.addShared(
                this,
                raw == null ? null : raw.toString(),
                subject == null ? null : subject.toString());

        if (added) {
            Toast.makeText(this, "Added to Amazon Invite Helper", Toast.LENGTH_SHORT).show();
            Intent changed = new Intent(MainActivity.ACTION_QUEUE_CHANGED);
            changed.setPackage(getPackageName());
            sendBroadcast(changed);
        } else {
            QueueStore.clearPendingCaptureTitle(this);
            Toast.makeText(this, "No Amazon link found in shared item", Toast.LENGTH_SHORT).show();
        }
    }
}
