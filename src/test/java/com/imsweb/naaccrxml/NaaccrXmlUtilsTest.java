/*
 * Copyright (C) 2015 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.junit.Assert;
import org.junit.Test;

import com.imsweb.naaccrxml.entity.Item;
import com.imsweb.naaccrxml.entity.NaaccrData;
import com.imsweb.naaccrxml.entity.Patient;
import com.imsweb.naaccrxml.entity.Tumor;

// TODO FPD beef up these tests; add cases for options, user dictionary and observer...
// TODO FPD add tests for line number on main entities...
public class NaaccrXmlUtilsTest {

    @Test
    public void testDateFormat() throws ParseException {
        // following examples are from http://books.xmlschemata.org/relaxng/ch19-77049.html

        assertValidDateValue("2001-10-26T21:32:52");
        assertValidDateValue("2001-10-26T21:32:52+02:00");
        assertValidDateValue("2001-10-26T19:32:52Z");
        assertValidDateValue("2001-10-26T19:32:52+00:00");
        assertValidDateValue("-2001-10-26T21:32:52");
        assertValidDateValue("2001-10-26T21:32:52.12679");

        // weird, the link says that the following is invalid, but the Java framework seems to accept it...
        assertValidDateValue("2001-10-26");

        assertInvalidDateValue("2001-10-26T21:32");
        assertInvalidDateValue("2001-10-26T25:32:52+02:00");
        assertInvalidDateValue("01-10-26T21:32");
    }

    private void assertValidDateValue(String dateValue) {
        try {
            DatatypeConverter.parseDateTime(dateValue);
        }
        catch (IllegalArgumentException e) {
            Assert.fail("Value should be valid, but isn't: " + dateValue);
        }
    }

    private void assertInvalidDateValue(String dateValue) {
        try {
            DatatypeConverter.parseDateTime(dateValue);
        }
        catch (IllegalArgumentException e) {
            return;
        }
        Assert.fail("Value should be invalid, but isn't: " + dateValue);
    }

    @Test
    public void testReadingXml() throws IOException {
        File file = new File(System.getProperty("user.dir") + "/src/test/resources/data/validation-standard-file.xml");

        // get the format from the file (not necessary, one could hard-code it in the reading call)
        String format = NaaccrXmlUtils.getFormatFromXmlFile(file);
        Assert.assertNotNull(format);

        // read the entire file at once
        NaaccrData data = NaaccrXmlUtils.readXmlFile(file, null, null, null);
        Assert.assertNotNull(data.getBaseDictionaryUri());
        Assert.assertNull(data.getUserDictionaryUri());
        Assert.assertNotNull(data.getRecordType());
        Assert.assertNotNull(data.getTimeGenerated());
        Assert.assertEquals(1, data.getItems().size());
        Assert.assertEquals(2, data.getPatients().size());

        // read the file using a stream
        try (PatientXmlReader reader = new PatientXmlReader(new FileReader(file), null, null, null)) {
            data = reader.getRootData();
            Assert.assertNotNull(data.getBaseDictionaryUri());
            Assert.assertNull(data.getUserDictionaryUri());
            Assert.assertNotNull(data.getRecordType());
            Assert.assertNotNull(data.getTimeGenerated());
            Assert.assertEquals(1, data.getItems().size());
            Assert.assertEquals(0, data.getPatients().size()); // this one is important, patients are not read right away in a stream!
            List<Patient> patients = new ArrayList<>();
            Patient patient = reader.readPatient();
            while (patient != null) {
                patients.add(patient);
                patient = reader.readPatient();
            }
            Assert.assertEquals(2, patients.size());
        }
    }

    @Test
    public void testWritingXml() throws IOException {
        NaaccrData data = new NaaccrData();
        data.setBaseDictionaryUri(NaaccrXmlDictionaryUtils.createUriFromVersion("140", true));
        data.setRecordType("I");
        data.setTimeGenerated(new Date());
        data.addItem(createItem("vendorName", "VENDOR"));
        Patient patient1 = new Patient();
        patient1.addItem(createItem("patientIdNumber", "00000001"));
        Tumor tumor1 = new Tumor();
        tumor1.addItem(createItem("primarySite", "C123"));
        patient1.addTumor(tumor1);
        data.addPatient(patient1);
        Patient patient2 = new Patient();
        patient2.addItem(createItem("patientIdNumber", "00000002"));
        data.addPatient(patient2);

        // write the entire file at once
        File file = new File(System.getProperty("user.dir") + "/build/test-writing-1.xml");
        NaaccrXmlUtils.writeXmlFile(data, file, null, null, null);

        // write the file using a steam
        file = new File(System.getProperty("user.dir") + "/build/test-writing-2.xml");
        try (PatientXmlWriter writer = new PatientXmlWriter(new FileWriter(file), data, null, null)) {
            for (Patient patient : data.getPatients())
                writer.writePatient(patient);
        }
    }

    @Test
    public void testFlatToXmlAndXmlToFlat() throws IOException {
        File xmlFile1 = new File(System.getProperty("user.dir") + "/src/test/resources/data/validation-standard-file.xml");
        NaaccrData data1 = NaaccrXmlUtils.readXmlFile(xmlFile1, null, null, null);

        File flatFile1 = new File(System.getProperty("user.dir") + "/build/test.txt");
        NaaccrXmlUtils.writeFlatFile(data1, flatFile1, null, null, null);
        data1 = NaaccrXmlUtils.readFlatFile(flatFile1, null, null, null);
        Assert.assertEquals("0000000001", data1.getItemValue("registryId"));

        File xmlFile2 = new File(System.getProperty("user.dir") + "/build/test.xml");
        NaaccrXmlUtils.flatToXml(flatFile1, xmlFile2, null, null, null);

        File flatFile2 = new File(System.getProperty("user.dir") + "/build/test2.txt");
        NaaccrXmlUtils.xmlToFlat(xmlFile2, flatFile2, null, null, null);
    }

    private Item createItem(String id, String value) {
        Item item = new Item();
        item.setNaaccrId(id);
        item.setValue(value);
        return item;
    }

    @Test
    public void testGetFormatFromXmlFile() {

        // regular file
        File file1 = new File(System.getProperty("user.dir") + "/src/test/resources/data/validation-standard-file.xml");
        Assert.assertEquals(NaaccrFormat.NAACCR_FORMAT_14_INCIDENCE, NaaccrXmlUtils.getFormatFromXmlFile(file1));

        // this one contains extensions
        File file2 = new File(System.getProperty("user.dir") + "/src/test/resources/data/validation-extension-missing-namespace.xml");
        Assert.assertEquals(NaaccrFormat.NAACCR_FORMAT_14_INCIDENCE, NaaccrXmlUtils.getFormatFromXmlFile(file2));
    }
}
