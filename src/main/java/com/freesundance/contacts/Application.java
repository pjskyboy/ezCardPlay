package com.freesundance.contacts;

import com.freesundance.contacts.google.ContactsExample;
import com.google.gdata.util.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Configuration
@ComponentScan
public class Application implements ResourceLoaderAware {

    @Autowired
    private ContactsExample contactsExample;

    @Autowired
    ApplicationContext ctx;

    private ResourceLoader resourceLoader;

    public void list() throws IOException, ServiceException, GeneralSecurityException {
        contactsExample.setP12FileResource(resourceLoader.getResource("classpath:Our Contacts-efbc1e2f31c3.p12"));
        contactsExample.listContacts();
    }

    public static void main(String[] args) throws IOException, ServiceException, GeneralSecurityException {
        ApplicationContext context =
                new AnnotationConfigApplicationContext(Application.class);
        Application application = context.getBean(Application.class);
        application.list();
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
