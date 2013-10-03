/*
* The contents of this file are subject to the Mozilla Public License
* Version 1.1 (the "License"); you may not use this file except in
* compliance with the License. You may obtain a copy of the License at
* http://www.mozilla.org/MPL/ 
* 
* Software distributed under the License is distributed on an "AS IS"
* basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
* License for the specific language governing rights and limitations under
* the License.
* 
* The Original Code is OpenELIS code.
* 
* Copyright (C) The Minnesota Department of Health.  All Rights Reserved.
*/

package org.bahmni.feed.openelis.feed.event;

import org.apache.log4j.Logger;
import org.bahmni.feed.openelis.AtomFeedProperties;
import org.bahmni.feed.openelis.ObjectMapperRepository;
import org.bahmni.feed.openelis.externalreference.dao.ExternalReferenceDao;
import org.bahmni.feed.openelis.externalreference.daoimpl.ExternalReferenceDaoImpl;
import org.bahmni.feed.openelis.externalreference.valueholder.ExternalReference;
import org.bahmni.feed.openelis.feed.contract.openmrs.encounter.OpenMRSEncounter;
import org.bahmni.feed.openelis.feed.contract.openmrs.encounter.OpenMRSOrder;
import org.bahmni.feed.openelis.feed.mapper.encounter.OpenMRSEncounterMapper;
import org.bahmni.feed.openelis.utils.AuditingService;
import org.bahmni.webclients.WebClient;
import org.ict4h.atomfeed.client.domain.Event;
import us.mn.state.health.lims.address.valueholder.OrganizationAddress;
import us.mn.state.health.lims.common.exception.LIMSRuntimeException;
import us.mn.state.health.lims.common.util.DateUtil;
import us.mn.state.health.lims.common.util.SystemConfiguration;
import us.mn.state.health.lims.hibernate.ElisHibernateSession;
import us.mn.state.health.lims.hibernate.HibernateUtil;
import us.mn.state.health.lims.login.daoimpl.LoginDAOImpl;
import us.mn.state.health.lims.observationhistory.valueholder.ObservationHistory;
import us.mn.state.health.lims.organization.dao.OrganizationTypeDAO;
import us.mn.state.health.lims.organization.daoimpl.OrganizationTypeDAOImpl;
import us.mn.state.health.lims.organization.valueholder.Organization;
import us.mn.state.health.lims.organization.valueholder.OrganizationType;
import us.mn.state.health.lims.panelitem.dao.PanelItemDAO;
import us.mn.state.health.lims.panelitem.daoimpl.PanelItemDAOImpl;
import us.mn.state.health.lims.panelitem.valueholder.PanelItem;
import us.mn.state.health.lims.patient.dao.PatientDAO;
import us.mn.state.health.lims.patient.daoimpl.PatientDAOImpl;
import us.mn.state.health.lims.patient.valueholder.Patient;
import us.mn.state.health.lims.requester.dao.RequesterTypeDAO;
import us.mn.state.health.lims.requester.daoimpl.RequesterTypeDAOImpl;
import us.mn.state.health.lims.requester.valueholder.RequesterType;
import us.mn.state.health.lims.sample.bean.SampleTestCollection;
import us.mn.state.health.lims.sample.daoimpl.SampleDAOImpl;
import us.mn.state.health.lims.sample.util.AccessionNumberUtil;
import us.mn.state.health.lims.sample.util.AnalysisBuilder;
import us.mn.state.health.lims.sample.valueholder.Sample;
import us.mn.state.health.lims.samplehuman.valueholder.SampleHuman;
import us.mn.state.health.lims.sampleitem.valueholder.SampleItem;
import us.mn.state.health.lims.samplesource.dao.SampleSourceDAO;
import us.mn.state.health.lims.samplesource.daoimpl.SampleSourceDAOImpl;
import us.mn.state.health.lims.siteinformation.daoimpl.SiteInformationDAOImpl;
import us.mn.state.health.lims.statusofsample.util.StatusOfSampleUtil;
import us.mn.state.health.lims.test.dao.TestDAO;
import us.mn.state.health.lims.test.daoimpl.TestDAOImpl;
import us.mn.state.health.lims.test.valueholder.Test;
import us.mn.state.health.lims.typeofsample.dao.TypeOfSampleDAO;
import us.mn.state.health.lims.typeofsample.dao.TypeOfSampleTestDAO;
import us.mn.state.health.lims.typeofsample.daoimpl.TypeOfSampleDAOImpl;
import us.mn.state.health.lims.typeofsample.daoimpl.TypeOfSampleTestDAOImpl;
import us.mn.state.health.lims.typeofsample.valueholder.TypeOfSampleTest;
import us.mn.state.health.lims.upload.action.AddSampleService;

import java.io.IOException;
import java.net.URI;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//analogous to a controller because it receives the request which is an event in this case
public class EncounterFeedWorker extends OpenElisEventWorker {
    private WebClient webClient;
    private String urlPrefix;
    private StringBuilder panelIdsBuilder = new StringBuilder();

    private AuditingService auditingService;

    private SampleDAOImpl sampleDAO;

    private ExternalReferenceDao externalReferenceDao;
    private PanelItemDAO panelItemDAO;
    private TypeOfSampleTestDAO typeOfSampleTestDAO;
    private TypeOfSampleDAO typeOfSampleDAO;
    private RequesterTypeDAO requesterTypeDAO;
    private OrganizationTypeDAO organizationTypeDAO;
    private SampleSourceDAO sampleSourceDAO;
    private PatientDAO patientDAO;

    private static long provider_requester_type_id;
    private static String referring_org_type_id;
    private static Logger logger = Logger.getLogger(EncounterFeedWorker.class);
    private TestDAO testDAO;

    public EncounterFeedWorker(WebClient webClient, String urlPrefix, ExternalReferenceDao externalReferenceDao,
                               AuditingService auditingService, PanelItemDAO panelItemDAO,
                               TypeOfSampleTestDAO typeOfSampleTestDAO, TypeOfSampleDAO typeOfSampleDAO,
                               RequesterTypeDAO requesterTypeDAO, OrganizationTypeDAO organizationTypeDAO,
                               SampleSourceDAO sampleSourceDAO, SampleDAOImpl sampleDAO, PatientDAO patientDAO,
                               TestDAO testDAO) {
        this.webClient = webClient;
        this.urlPrefix = urlPrefix;
        this.externalReferenceDao = externalReferenceDao;
        this.auditingService = auditingService;
        this.panelItemDAO = panelItemDAO;
        this.typeOfSampleTestDAO = typeOfSampleTestDAO;
        this.typeOfSampleDAO = typeOfSampleDAO;
        this.requesterTypeDAO = requesterTypeDAO;
        this.organizationTypeDAO = organizationTypeDAO;
        this.sampleSourceDAO = sampleSourceDAO;
        this.sampleDAO = sampleDAO;
        this.patientDAO = patientDAO;
        this.testDAO = testDAO;
    }

    public EncounterFeedWorker(WebClient authenticatedWebClient, String urlPrefix) {
        this(authenticatedWebClient, urlPrefix, new ExternalReferenceDaoImpl(),
                new AuditingService(new LoginDAOImpl(), new SiteInformationDAOImpl()),
                new PanelItemDAOImpl(), new TypeOfSampleTestDAOImpl(), new TypeOfSampleDAOImpl(),
                new RequesterTypeDAOImpl(), new OrganizationTypeDAOImpl(), new SampleSourceDAOImpl(),
                new SampleDAOImpl(), new PatientDAOImpl(), new TestDAOImpl());
    }

    @Override
    public void process(Event event) {
        try {
            String content = event.getContent();
            String encounterJSON = webClient.get(URI.create(urlPrefix + content), new HashMap<String, String>(0));

            if (process(encounterJSON)) return;

        } catch (IOException e) {
            throw new LIMSRuntimeException(e);
        } finally {
            ElisHibernateSession session = (ElisHibernateSession) HibernateUtil.getSession();
            session.clearSession();
        }
    }

    public boolean process(String encounterJSON) throws IOException {
        OpenMRSEncounterMapper openMRSEncounterMapper = new OpenMRSEncounterMapper(ObjectMapperRepository.objectMapper);
        OpenMRSEncounter openMRSEncounter = openMRSEncounterMapper.map(encounterJSON);
        logInfo(openMRSEncounter);

        if (!openMRSEncounter.hasLabOrder())
            return true;

        List<OpenMRSOrder> labOrders = openMRSEncounter.getLabOrders();
        if (labOrders.isEmpty())
            return true;

        String sysUserId = auditingService.getSysUserId();
        String accessionNumber = AccessionNumberUtil.getNextAccessionNumber("");

        Date nowAsSqlDate = DateUtil.getNowAsSqlDate();
        Sample sample = getSample(sysUserId, accessionNumber, nowAsSqlDate, openMRSEncounter);

        List<SampleTestCollection> sampleTestCollections = new ArrayList<>();
        int sampleItemIdIndex = 0;

        String patientUUID = openMRSEncounter.getPatient().getUuid();
        Patient patient = patientDAO.getPatientByUUID(patientUUID);

        for (OpenMRSOrder labOrder : labOrders) {
            sampleItemIdIndex++;

            List<Test> tests = getTests(labOrder);

            // TODO : Mujir - is this ok? Does this work for panels?????? HIGH PRIORITY
            String anyTestId = tests.get(0).getId();
            TypeOfSampleTest typeOfSampleTestForTest = typeOfSampleTestDAO.getTypeOfSampleTestForTest(anyTestId);

            // TODO : Mujir - should this be empty??
            String collector = "";

            SampleItem item = new SampleItem();
            item.setSysUserId(sysUserId);
            item.setSample(sample);
            item.setTypeOfSample(typeOfSampleDAO.getTypeOfSampleById(typeOfSampleTestForTest.getTypeOfSampleId()));
            item.setSortOrder(Integer.toString(sampleItemIdIndex));
            item.setStatusId(StatusOfSampleUtil.getStatusID(StatusOfSampleUtil.SampleStatus.Entered));
            item.setCollector(collector);

            // TODO : Mujir - is empty ObservationHistory, initialConditionList ok?
            List<ObservationHistory> initialConditionList = new ArrayList<>();

            // TODO : Mujir - is DateUtil.convertSqlDateToStringDate(nowAsSqlDate) ok?
            SampleTestCollection sampleTestCollection = new SampleTestCollection(item, tests,
                    DateUtil.formatDateTimeAsText(nowAsSqlDate), initialConditionList);
            sampleTestCollections.add(sampleTestCollection);
        }

        // TODO : Mujir - is empty observations ok?
        List<ObservationHistory> observations = new ArrayList<>();

        SampleHuman sampleHuman = new SampleHuman();
        sampleHuman.setSysUserId(sysUserId);

        String projectId = null;

        RequesterType providerRequesterType = requesterTypeDAO.getRequesterTypeByName("provider");
        if (providerRequesterType != null) {
            provider_requester_type_id = Long.parseLong(providerRequesterType.getId());
        }

        OrganizationType orgType = organizationTypeDAO.getOrganizationTypeByName("referring clinic");
        if (orgType != null) {
            referring_org_type_id = orgType.getId();
        }

        // TODO :Mujir - providerId?
        String providerId = null;

        AnalysisBuilder analysisBuilder = getAnalysisBuilder();

        AddSampleService addSampleService = new AddSampleService(false);
        addSampleService.persist(analysisBuilder, false, null, null,
                new ArrayList<OrganizationAddress>(), sample,
                sampleTestCollections, observations, sampleHuman, patient.getId(), projectId, providerId, sysUserId,
                provider_requester_type_id,  referring_org_type_id);

        return false;
    }

    private List<Test> getTests(OpenMRSOrder labOrder) {
        String externalReferenceTestOrPanelUUID = labOrder.getTestOrPanelUUID();
        if (labOrder.isLabOrderForPanel()) {
            return getTestsForPanel(externalReferenceTestOrPanelUUID);
        }

        return getTest(externalReferenceTestOrPanelUUID);
    }

    private List<Test> getTest(String externalReferenceTestOrPanelUUID) {
        String productTypeTest = AtomFeedProperties.getInstance().getProductTypeLabTest();
        ExternalReference data = externalReferenceDao.getData(externalReferenceTestOrPanelUUID, productTypeTest);
        long testId = data.getItemId();
        Test test = testDAO.getTestById(String.valueOf(testId));

        List<Test> tests = new ArrayList<>();
        tests.add(test);
        return tests;
    }

    private List<Test> getTestsForPanel(String externalReferencePanelUUID) {
        List<Test> tests = new ArrayList<>();
        String productTypePanel = AtomFeedProperties.getInstance().getProductTypePanel();
        ExternalReference data = externalReferenceDao.getData(externalReferencePanelUUID, productTypePanel);
        long panelId = data.getItemId();
        List panelItemsForPanel = panelItemDAO.getPanelItemsForPanel(String.valueOf(panelId));
        for (Object obj : panelItemsForPanel) {
            PanelItem panelItem = (PanelItem) obj;
            panelIdsBuilder.append(panelItem.getPanel().getId()).append(",");
            tests.add(panelItem.getTest());
        }

        return tests;
    }

    private Sample getSample(String sysUserId, String accessionNumber, Date nowAsSqlDate, OpenMRSEncounter openMRSEncounter) {
        Sample sample = new Sample();
        sample.setSysUserId(sysUserId);
        sample.setAccessionNumber(accessionNumber);

        // TODO : Mujir - remove this hardcoding??? Read this from the event???
        sample.setSampleSource(sampleSourceDAO.getByName("OPD"));

        // TODO : Mujir - remove this hardcoding???? Read this from the event????
        sample.setEnteredDate(nowAsSqlDate);
        sample.setReceivedDate(nowAsSqlDate);
//        if (useReceiveDateForCollectionDate) {
//            sample.setCollectionDateForDisplay(collectionDateFromRecieveDate);
//        }

        sample.setDomain(SystemConfiguration.getInstance().getHumanDomain());
        sample.setStatusId(StatusOfSampleUtil.getStatusID(StatusOfSampleUtil.OrderStatus.Entered));

        // Mujir - create an external reference to order id in openelis.. this can go in Sample against the accession number
        sample.setUUID(openMRSEncounter.getUuid());

        return sample;
    }

    private Organization getOrganization(String sysUserId) {
        Organization organization = new Organization();

        organization.setOrganizationName("");
        organization.setShortName("");
        // this was left as a warning for copy and paste -- it causes a null
        // pointer exception in session.flush()
        // newOrganization.setOrganizationTypes(ORG_TYPE_SET);
        organization.setSysUserId(sysUserId);
        organization.setMlsSentinelLabFlag("N");

        return organization;
    }

    private AnalysisBuilder getAnalysisBuilder() {
        AnalysisBuilder analysisBuilder = new AnalysisBuilder();
        analysisBuilder.augmentPanelIdToPanelMap(panelIdsBuilder.toString());
        return analysisBuilder;
    }

    private void logInfo(OpenMRSEncounter openMRSEncounter) {
        logger.info(String.format("Processing encounter with ID=%s", openMRSEncounter.getUuid()));
    }
}