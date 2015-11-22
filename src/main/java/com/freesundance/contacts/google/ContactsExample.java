/* Copyright (c) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.freesundance.contacts.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.Query;
import com.google.gdata.client.Service;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.client.http.HttpGDataRequest;
import com.google.gdata.data.DateTime;
import com.google.gdata.data.Link;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.contacts.ContactGroupFeed;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.util.NoLongerAvailableException;
import com.google.gdata.util.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

/**
 * Example command-line utility that demonstrates how to use the Google Data API
 * Java client libraries for Contacts. The example allows to run all the basic
 * contact related operations such as adding new contact, listing all contacts,
 * updating existing contacts, deleting the contacts.
 * <p/>
 * Full documentation about the API can be found at:
 * http://code.google.com/apis/contacts/
 */
@Component
public class ContactsExample {

    private static final Logger LOG = LoggerFactory.getLogger(ContactsExample.class);

    private enum SystemGroup {
        MY_CONTACTS("Contacts", "My Contacts"),
        FRIENDS("Friends", "Friends"),
        FAMILY("Family", "Family"),
        COWORKERS("Coworkers", "Coworkers");

        private final String systemGroupId;
        private final String prettyName;

        SystemGroup(String systemGroupId, String prettyName) {
            this.systemGroupId = systemGroupId;
            this.prettyName = prettyName;
        }

        static SystemGroup fromSystemGroupId(String id) {
            for (SystemGroup group : SystemGroup.values()) {
                if (id.equals(group.systemGroupId)) {
                    return group;
                }
            }
            throw new IllegalArgumentException("Unrecognized system group id: " + id);
        }

        @Override
        public String toString() {
            return prettyName;
        }
    }

    private static final String DEFAULT_FEED = "https://www.google.com/m8/feeds/";
    private static final String DEFAULT_PROJECTION = "thin";

    /**
     * Base URL for the feed
     */
    private URL feedUrl;

    /**
     * Service used to communicate with contacts feed.
     */
    private ContactsService service;

    /**
     * Projection used for the feed
     */
    private String projection;

    private Resource p12FileResource;

    public Resource getP12FileResource() {
        return p12FileResource;
    }

    public void setP12FileResource(Resource p12FileResource) {
        this.p12FileResource = p12FileResource;
    }

    public URL getFeedUrl() {
        return feedUrl;
    }

    public ContactsService getService() {
        return service;
    }

    public String getProjection() {
        return projection;
    }

    public static java.util.logging.Logger getHttpRequestLogger() {
        return httpRequestLogger;
    }

    /**
     * Reference to the Logger for setting verbose mode.
     */
    private static final java.util.logging.Logger httpRequestLogger =
            java.util.logging.Logger.getLogger(HttpGDataRequest.class.getName());

    public ContactsExample() {
        try {
            projection = DEFAULT_PROJECTION;
            String url = DEFAULT_FEED
                    + "contacts/default/" + projection;

            LOG.debug("url [{}]", url);

            feedUrl = new URL(url);
                    } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Contacts Example.
     *
     * @param parameters command line parameters
     */
    public ContactsExample(ContactsExampleParameters parameters) throws IOException, ServiceException, GeneralSecurityException {
        projection = parameters.getProjection();
        String url = parameters.getBaseUrl()
                + (parameters.isGroupFeed() ? "groups/" : "contacts/")
                + "default/" + projection;

        feedUrl = new URL(url);
        service = authenticate();
    }


    private ContactsService authenticate() throws GeneralSecurityException, IOException, ServiceException {
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        String APPLICATION_NAME = "juppfamily-contacts-1";
        String SERVICE_ACCOUNT_EMAIL = "account-1@our-contacts-1136.iam.gserviceaccount.com";
        String accountUser = "nostro@juppfamily.info";

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(SERVICE_ACCOUNT_EMAIL)
                .setServiceAccountScopes(
                        Collections.singleton("https://www.google.com/m8/feeds/"))
                .setServiceAccountPrivateKeyFromP12File(p12FileResource.getFile())
                .setServiceAccountUser(accountUser)
                .build();

        if (!credential.refreshToken()) {
            throw new RuntimeException("Failed OAuth to refresh the token");
        }

        ContactsService service = new ContactsService(APPLICATION_NAME);
        service.setOAuth2Credentials(credential);
        service.setHeader("GData-Version", "3.0");

        return service;
    }

    /**
     * Deletes a contact or a group
     *
     * @param parameters the parameters determining contact to delete.
     */
    private void deleteEntry(ContactsExampleParameters parameters)
            throws IOException, ServiceException {
        if (parameters.isGroupFeed()) {
            // get the Group then delete it
            ContactGroupEntry group = getGroupInternal(parameters.getId());
            if (group == null) {
                LOG.debug("No Group found with id: " + parameters.getId());
                return;
            }
            group.delete();
        } else {
            // get the contact then delete them
            ContactEntry contact = getContactInternal(parameters.getId());
            if (contact == null) {
                LOG.debug("No contact found with id: " + parameters.getId());
                return;
            }
            contact.delete();
        }
    }

    /**
     * Updates a contact or a group. Presence of any property of a given kind
     * (im, phone, mail, etc.) causes the existing properties of that kind to be
     * replaced.
     *
     * @param parameters parameters storing updated contact values.
     */
    public void updateEntry(ContactsExampleParameters parameters)
            throws IOException, ServiceException {
        if (parameters.isGroupFeed()) {
            ContactGroupEntry group = buildGroup(parameters);
            // get the group then update it
            ContactGroupEntry canonicalGroup = getGroupInternal(parameters.getId());

            canonicalGroup.setTitle(group.getTitle());
            canonicalGroup.setContent(group.getContent());
            // update fields
            List<ExtendedProperty> extendedProperties =
                    canonicalGroup.getExtendedProperties();
            extendedProperties.clear();
            if (group.hasExtendedProperties()) {
                extendedProperties.addAll(group.getExtendedProperties());
            }
            printGroup(canonicalGroup.update());
        } else {
            ContactEntry contact = buildContact(parameters);
            // get the contact then update it
            ContactEntry canonicalContact = getContactInternal(parameters.getId());
            ElementHelper.updateContact(canonicalContact, contact);
            printContact(canonicalContact.update());
        }
    }

    /**
     * Gets a contact by it's id.
     *
     * @param id the id of the contact.
     * @return the ContactEntry or null if not found.
     */
    private ContactEntry getContactInternal(String id)
            throws IOException, ServiceException {
        return service.getEntry(
                new URL(id.replace("/base/", "/" + projection + "/")),
                ContactEntry.class);
    }

    /**
     * Gets a Group by it's id.
     *
     * @param id the id of the group.
     * @return the GroupEntry or null if not found.
     */
    private ContactGroupEntry getGroupInternal(String id)
            throws IOException, ServiceException {
        return service.getEntry(
                new URL(id.replace("/base/", "/" + projection + "/")),
                ContactGroupEntry.class);
    }

    /**
     * Print the contents of a ContactEntry to System.err.
     *
     * @param contact The ContactEntry to display.
     */
    private static void printContact(ContactEntry contact) {
        LOG.debug("Id: " + contact.getId());
        if (contact.getTitle() != null) {
            LOG.debug("Contact name: " + contact.getTitle().getPlainText());
        } else {
            LOG.debug("Contact has no name");

        }
        LOG.debug("Last updated: " + contact.getUpdated().toUiString());
        if (contact.hasDeleted()) {
            LOG.debug("Deleted:");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ElementHelper.printContact(ps, contact);
        LOG.info("\n{}", baos.toString());

        Link photoLink = contact.getLink(
                "http://schemas.google.com/contacts/2008/rel#photo", "image/*");
        LOG.debug("Photo link: " + photoLink.getHref());
        String photoEtag = photoLink.getEtag();
        LOG.debug("  Photo ETag: "
                + (photoEtag != null ? photoEtag : "(No contact photo uploaded)"));
        LOG.debug("Self link: " + contact.getSelfLink().getHref());
        LOG.debug("Edit link: " + contact.getEditLink().getHref());
        LOG.debug("ETag: " + contact.getEtag());
        LOG.debug("-------------------------------------------\n");
    }

    /**
     * Prints the contents of a GroupEntry to System.err
     *
     * @param groupEntry The GroupEntry to display
     */
    private static void printGroup(ContactGroupEntry groupEntry) {
        LOG.debug("Id: " + groupEntry.getId());
        LOG.debug("Group Name: " + groupEntry.getTitle().getPlainText());
        LOG.debug("Last Updated: " + groupEntry.getUpdated());
        LOG.debug("Extended Properties:");
        for (ExtendedProperty property : groupEntry.getExtendedProperties()) {
            if (property.getValue() != null) {
                LOG.debug("  " + property.getName() + "(value) = " +
                        property.getValue());
            } else if (property.getXmlBlob() != null) {
                LOG.debug("  " + property.getName() + "(xmlBlob) = " +
                        property.getXmlBlob().getBlob());
            }
        }

        LOG.debug("Which System Group: ");
        if (groupEntry.hasSystemGroup()) {
            SystemGroup systemGroup
                    = SystemGroup.fromSystemGroupId(groupEntry.getSystemGroup().getId());
            LOG.debug("systemGroup [{}]", systemGroup);
        } else {
            LOG.debug("(Not a system group)");
        }

        LOG.debug("Self Link [{}]", groupEntry.getSelfLink().getHref());
        if (!groupEntry.hasSystemGroup()) {
            // System groups are not modifiable, and thus don't have an edit link.
            LOG.debug("Edit Link [{}]", groupEntry.getEditLink().getHref());
        }
        LOG.debug("-------------------------------------------\n");
    }

    /**
     * Performs action specified as action parameter.
     *
     * @param example    object controlling the execution
     * @param parameters parameters from command line or script
     */
    private static void processAction(ContactsExample example,
                                      ContactsExampleParameters parameters) throws IOException,
            ServiceException, GeneralSecurityException {
        ContactsExampleParameters.Actions action = parameters.getAction();
        LOG.debug("Executing action: " + action);
        switch (action) {
            case LIST:
                example.listEntries(parameters);
                break;
            case QUERY:
                example.queryEntries(parameters);
                break;
            case ADD:
                example.addEntry(parameters);
                break;
            case DELETE:
                example.deleteEntry(parameters);
                break;
            case UPDATE:
                example.updateEntry(parameters);
                break;
            default:
                LOG.debug("No such action");
        }
    }

    /**
     * Query entries (Contacts/Groups) according to parameters specified.
     *
     * @param parameters parameter for contact quest
     */
    private void queryEntries(ContactsExampleParameters parameters)
            throws IOException, ServiceException {
        Query myQuery = new Query(feedUrl);
        if (parameters.getUpdatedMin() != null) {
            DateTime startTime = DateTime.parseDateTime(parameters.getUpdatedMin());
            myQuery.setUpdatedMin(startTime);
        }
        if (parameters.getMaxResults() != null) {
            myQuery.setMaxResults(parameters.getMaxResults().intValue());
        }
        if (parameters.getStartIndex() != null) {
            myQuery.setStartIndex(parameters.getStartIndex());
        }
        if (parameters.isShowDeleted()) {
            myQuery.setStringCustomParameter("showdeleted", "true");
        }
        if (parameters.getRequireAllDeleted() != null) {
            myQuery.setStringCustomParameter("requirealldeleted",
                    parameters.getRequireAllDeleted());
        }
        if (parameters.getSortorder() != null) {
            myQuery.setStringCustomParameter("sortorder", parameters.getSortorder());
        }
        if (parameters.getOrderBy() != null) {
            myQuery.setStringCustomParameter("orderby", parameters.getOrderBy());
        }
        if (parameters.getGroup() != null) {
            myQuery.setStringCustomParameter("group", parameters.getGroup());
        }
        try {
            if (parameters.isGroupFeed()) {
                ContactGroupFeed groupFeed = service.query(
                        myQuery, ContactGroupFeed.class);
                for (ContactGroupEntry entry : groupFeed.getEntries()) {
                    printGroup(entry);
                }
                LOG.debug("Total: " + groupFeed.getEntries().size()
                        + " entries found");
            } else {
                ContactFeed resultFeed = service.query(myQuery, ContactFeed.class);
                for (ContactEntry entry : resultFeed.getEntries()) {
                    printContact(entry);
                }
                LOG.debug("Total: " + resultFeed.getEntries().size()
                        + " entries found");
            }
        } catch (NoLongerAvailableException ex) {
            LOG.debug(
                    "Not all placehorders of deleted entries are available");
        }
    }


    public void listContacts() throws IOException, ServiceException, GeneralSecurityException {

        service = authenticate();

        ContactFeed resultFeed = service.getFeed(feedUrl, ContactFeed.class);
        // Print the results
        LOG.debug(resultFeed.getTitle().getPlainText());
        for (ContactEntry entry : resultFeed.getEntries()) {
            printContact(entry);
            // Since 2.0, the photo link is always there, the presence of an actual
            // photo is indicated by the presence of an ETag.
            Link photoLink = entry.getLink(
                    "http://schemas.google.com/contacts/2008/rel#photo", "image/*");
            if (photoLink.getEtag() != null) {
                Service.GDataRequest request =
                        service.createLinkQueryRequest(photoLink);
                request.execute();
                InputStream in = request.getResponseStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                RandomAccessFile file = new RandomAccessFile(
                        "/tmp/" + entry.getSelfLink().getHref().substring(
                                entry.getSelfLink().getHref().lastIndexOf('/') + 1), "rw");
                byte[] buffer = new byte[4096];
                for (int read = 0; (read = in.read(buffer)) != -1;
                     out.write(buffer, 0, read)) {
                }
                file.write(out.toByteArray());
                file.close();
                in.close();
                request.end();
            }
            LOG.debug("Total: " + resultFeed.getEntries().size()
                    + " entries found");
        }
    }

    /**
     * List Contacts or Group entries (no parameter are taken into account)
     * Note! only 25 results will be returned - this is default.
     *
     * @param parameters
     */
    private void listEntries(ContactsExampleParameters parameters)
            throws IOException, ServiceException, GeneralSecurityException {
        if (parameters.isGroupFeed()) {
            ContactGroupFeed groupFeed =
                    service.getFeed(feedUrl, ContactGroupFeed.class);
            LOG.debug(groupFeed.getTitle().getPlainText());
            for (ContactGroupEntry entry : groupFeed.getEntries()) {
                printGroup(entry);
            }
            LOG.debug("Total: " + groupFeed.getEntries().size() +
                    " groups found");
        } else {
            listContacts();
        }

    }

    /**
     * Adds contact or group entry according to the parameters specified.
     *
     * @param parameters parameters for contact adding
     */
    private void addEntry(ContactsExampleParameters parameters)
            throws IOException, ServiceException {
        if (parameters.isGroupFeed()) {
            ContactGroupEntry addedGroup =
                    service.insert(feedUrl, buildGroup(parameters));
            printGroup(addedGroup);
            //lastAddedId = addedGroup.getId();
        } else {
            ContactEntry addedContact =
                    service.insert(feedUrl, buildContact(parameters));
            printContact(addedContact);
            // Store id of the added contact so that scripts can use it in next steps
            //lastAddedId = addedContact.getId();
        }
    }

    /**
     * Build ContactEntry from parameters.
     *
     * @param parameters parameters
     * @return A contact.
     */
    private static ContactEntry buildContact(
            ContactsExampleParameters parameters) {
        ContactEntry contact = new ContactEntry();
        ElementHelper.buildContact(contact, parameters.getElementDesc());
        return contact;
    }

    /**
     * Builds GroupEntry from parameters
     *
     * @param parameters ContactExamplParameters
     * @return GroupEntry Object
     */
    private static ContactGroupEntry buildGroup(
            ContactsExampleParameters parameters) {
        ContactGroupEntry groupEntry = new ContactGroupEntry();
        ElementHelper.buildGroup(groupEntry, parameters.getElementDesc());
        return groupEntry;
    }

    /**
     * Displays usage information.
     */
    private static void displayUsage() {


        String usageInstructions =
                "USAGE:\n"
                        + " -----------------------------------------------------------\n"
                        + "  Basic command line usage:\n"
                        + "    ContactsExample [<options>] <authenticationInformation> "
                        + "<--contactfeed|--groupfeed> "
                        + "--action=<action> [<action options>]  "
                        + "(default contactfeed)\n"
                        + "  Scripting commands usage:\n"
                        + "    contactsExample [<options>] <authenticationInformation> "
                        + "<--contactfeed|--groupfeed>   --script=<script file>  "
                        + "(default contactFeed) \n"
                        + "  Print usage (this screen):\n"
                        + "   --help\n"
                        + " -----------------------------------------------------------\n\n"
                        + "  Options: \n"
                        + "    --base-url=<url to connect to> "
                        + "(default http://www.google.com/m8/feeds/) \n"
                        + "    --projection=[thin|full|property-KEY] "
                        + "(default thin)\n"
                        + "    --verbose : dumps communication information\n"
                        + "  Authentication Information (obligatory on command line): \n"
                        + "    --username=<username email> --password=<password>\n"
                        + "  Actions: \n"
                        + "     * list  list all contacts\n"
                        + "     * query  query contacts\n"
                        + "        options:\n"
                        + "             --showdeleted : shows also deleted contacts\n"
                        + "             --updated-min=YYYY-MM-DDTHH:MM:SS : only updated "
                        + "after the time specified\n"
                        + "             --requre-all-deleted=[true|false] : specifies "
                        + "server behaviour in case of placeholders for deleted entries are"
                        + "lost. Relevant only if --showdeleted and --updated-min also "
                        + "provided.\n"
                        + "             --orderby=lastmodified : order by last modified\n"
                        + "             --sortorder=[ascending|descending] : sort order\n"
                        + "             --max-results=<n> : return maximum n results\n"
                        + "             --start-index=<n> : return results starting from "
                        + "the starting index\n"
                        + "             --querygroupid=<groupid> : return results from the "
                        + "group\n"
                        + "    * add  add new contact\n"
                        + "        options:\n"
                        + ElementHelper.getUsageString()
                        + "    * delete  delete contact\n"
                        + "        options:\n"
                        + "             --id=<contact id>\n"
                        + "    * update  updates contact\n"
                        + "        options:\n"
                        + "             --id=<contact id>\n"
                        + ElementHelper.getUsageString();

        LOG.debug(usageInstructions);
    }

    /**
     * Run the example program.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) throws ServiceException, IOException, GeneralSecurityException {

        ContactsExampleParameters parameters = new ContactsExampleParameters(args);
        if (parameters.isVerbose()) {
            httpRequestLogger.setLevel(Level.FINEST);
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.FINEST);
            httpRequestLogger.addHandler(handler);
            httpRequestLogger.setUseParentHandlers(false);
        }

        if (parameters.numberOfParameters() == 0 || parameters.isHelp()
                || (parameters.getAction() == null && parameters.getScript() == null)) {
            displayUsage();
            return;
        }

        // Check that at most one of contactfeed and groupfeed has been provided
        if (parameters.isContactFeed() && parameters.isGroupFeed()) {
            throw new RuntimeException("Only one of contactfeed / groupfeed should" +
                    "be specified");
        }

        ContactsExample example = new ContactsExample(parameters);

        processAction(example, parameters);
        System.out.flush();
    }
}
