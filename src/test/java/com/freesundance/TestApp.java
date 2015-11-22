package com.freesundance;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.StructuredName;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.List;

/**
 * Created by pjj on 21/11/15.
 */
public class TestApp {

    Logger logger = LoggerFactory.getLogger(TestApp.class);
    @Test public void load() throws Exception {
        List<VCard> vCards = Ezvcard.parse(IOUtils.toString(new FileInputStream("./target/test-classes/HomePhoneDirectory.vcf"))).all();

        org.junit.Assert.assertEquals(77, vCards.size());
        for (VCard vCard : vCards) {
            StructuredName structuredName = vCard.getStructuredName();
            String given = structuredName.getGiven() != null ? structuredName.getGiven().trim() : "";
            String space = given.length() > 0 ? " " : "";
            String family = structuredName.getFamily() != null ? structuredName.getFamily().trim() : "";
            space = family.length() == 0 ? space.trim() : space;
            logger.info("[{}{}{}]", given, space, family);
        }
    }


}
