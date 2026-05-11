package com.adamoutler.ssh.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@RunWith(AndroidJUnit4::class)
class BackupRulesVerificationTest {

    @Test
    fun testDataExtractionRulesExcludeSensitiveData() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val resId = context.resources.getIdentifier("data_extraction_rules", "xml", context.packageName)
        assertTrue("data_extraction_rules.xml must exist in resources", resId != 0)
        
        val parser = context.resources.getXml(resId)
        var hasProfiles = false
        var hasIdentities = false
        var hasKnownHosts = false
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                val domain = parser.getAttributeValue(null, "domain")
                val path = parser.getAttributeValue(null, "path")
                
                if (domain == "sharedpref" && path == "secret_ssh_profiles.xml") hasProfiles = true
                if (domain == "sharedpref" && path == "secret_ssh_identities.xml") hasIdentities = true
                if (domain == "file" && path == "ssh_known_hosts") hasKnownHosts = true
            }
            eventType = parser.next()
        }
        
        assertTrue("data_extraction_rules must exclude secret_ssh_profiles.xml", hasProfiles)
        assertTrue("data_extraction_rules must exclude secret_ssh_identities.xml", hasIdentities)
        assertTrue("data_extraction_rules must exclude ssh_known_hosts", hasKnownHosts)
    }

    @Test
    fun testBackupRulesExcludeSensitiveData() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val resId = context.resources.getIdentifier("backup_rules", "xml", context.packageName)
        assertTrue("backup_rules.xml must exist in resources", resId != 0)
        
        val parser = context.resources.getXml(resId)
        var hasProfiles = false
        var hasIdentities = false
        var hasKnownHosts = false
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                val domain = parser.getAttributeValue(null, "domain")
                val path = parser.getAttributeValue(null, "path")
                
                if (domain == "sharedpref" && path == "secret_ssh_profiles.xml") hasProfiles = true
                if (domain == "sharedpref" && path == "secret_ssh_identities.xml") hasIdentities = true
                if (domain == "file" && path == "ssh_known_hosts") hasKnownHosts = true
            }
            eventType = parser.next()
        }
        
        assertTrue("backup_rules must exclude secret_ssh_profiles.xml", hasProfiles)
        assertTrue("backup_rules must exclude secret_ssh_identities.xml", hasIdentities)
        assertTrue("backup_rules must exclude ssh_known_hosts", hasKnownHosts)
    }
}
