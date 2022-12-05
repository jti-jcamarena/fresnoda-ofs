/** ------------------------------------------------------------------------------------------------------------------
 * Odyssey File and Serve (OFS) defendant party reviewFiling interface - City of Fresno, CA
 * last updated 3/9/2020 by JCamarena
 * V1.0 by R.Short / Bit Link Solutions on 8/07/2019
 * . Initial release
 *
 * Purpose:
 * The purpose of this interface is to file cases w/ the Odyssey File & Serve system. The interface will create Case
 * reviewFiling xml messages that will be stored in a queue folder for the ESL service to pickup, x.509 sign and deliver
 * to OFS system for review/approval.
 *
 * Business Rule:
 * Code = Interface_OFS_CreateDefendantReviewFilingMessage
 * Name = Interface_OFS_CreateDefendantReviewFilingMessage
 * Category = OFS
 * -----------------------------------------------------------------------
 * Workflow Process1:
 *  Code = INT_OFS_P1
 *  Name = OFS Outbound - Submit To Court
 * Triggers:
 *  Rule: Document Status insert and OFS - OFSSUB Submit to Court
 * ----
 * Work Queue:
 *  Number = INT_OFS_P1.1
 *  Name = OFS Outbound - Submit To Court
 * Work Queue Rule:
 *  Interface Rule = OFS_INT_ProcessOutboundReviewFilingMessages
 * ----
 * Workflow Process2:
 *  Code = INT_OFS_P3
 *  Name = OFS Court Filing - Review
 * Triggers:
 *  Rule: Ct Interface Tracking Detail insert and OFS - Court Filing Review Que
 * ----
 * Work Queue:
 *  Number = INT_OFS_P3.1
 *  Name = OFS Court Filing - Review
 * Work Queue Rule:
 *  Screens = caseNumber=@{eventEntity.interfaceTracking.case}
 * -----------------------------------------------------------------------
 * OFS external variables:
 *  @input: _docStatus - Class:DocumentStatus
 *  @input: _ofsUniqueAttorneyId - Class:String - e8cacd46-ca46-411d-bbbe-b8a54ee432c4
 *  @input: _ofsUniqueAttorneyIdProduction f608c336-f9fb-46f1-a28b-0e8ed8da06aa
 *  @input: _ofsGenericStatuteCode - Class:String - court statute override
 *  @input: _eslOutboundFilingSmbPath - Class:String - \\\\torreypines\\OFS\\out\\processing
 *  @input: _ofsOutboundQueuedDirectory - Class:String \\\\torreypines\\OFS\\out\\queued
 *  @input: _submitAttorneyParticipant - Class:Boolean - true
 *  @input: _submitSSN - Class:Boolean - false
 *  @input: _formatSSN - Class:Boolean - false
 *  @input: _fileToStaging - Class:Boolean - false
 *  @input: _submitPersonSexCode - Class:Boolean - false
 *  @input: _submitCaseCrossReference - Class:Boolean - true
 *  @input: _submitRace - Class:Boolean - false
 *  @input: _submitStaticAttorney - Class:Boolean - true
 *	@output _eResult - Class:String (CtInterfaceTracking.result)
 */

import com.hazelcast.util.StringUtil;
import com.sustain.cases.model.*;

import com.sustain.expression.Where;
import com.sustain.lookuplist.model.LookupAttribute;
import com.sustain.lookuplist.model.LookupList;
import com.sustain.document.model.Document;
import com.sustain.person.model.PersonProfile;
import com.sustain.properties.model.SystemProperty;
import com.sustain.person.model.Address;
import com.sustain.person.model.Identification;
import groovy.xml.XmlUtil;
import groovy.util.slurpersupport.GPathResult;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.stream.Stream;
import java.io.BufferedWriter;
import java.io.FileWriter;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.apache.commons.io.FilenameUtils;

import java.time.LocalDateTime;
import java.sql.Timestamp;
// Insert used for Debugging Interface
/*
Case cCase = DomainObject.find(Case.class, 'caseNumber', '=', "19-13", maxResult(1))[0]
if( cCase ) {
	this._docStatus = cCase.collect("documents.statuses[statusType=='OFSSUB']").first();
	if( !this._docStatus )
		return;
} else
	return;
*/

/** ------------------------------------------------------------------------------------
 * Interface execution begins here
 */
HashSet filingParties = new HashSet();
filingParties.addAll(_docStatus.document.collect("xrefs[refType == 'DOCREL' && entityType=='SubCase'].ref[!parties.isEmpty()].parties[partyType == 'DEF' && status == 'ACT']"));
if (filingParties.isEmpty()){
  filingParties.add(_docStatus.document.case.collect("parties[partyType == 'DEF' && status == 'ACT']").orderBy("id").first());
}
for (filingParty in filingParties){
	new CreateDefPtyReviewFilingInterface(this).exec(filingParty);
}

/** ------------------------------------------------------------------------------------
 * Interface Tracking class
 */
class MyInterfaceTracking {
	CtInterfaceTracking tracking_;

	static final String INTERFACE_ = "Interface_OFS_CreateDefendantReviewFilingMessage";
	static final String INTERFACE_TYPE_ = "OFS_RVWFILE_SUB";		// Process review filing messages

	// Tracking error result codes [CL_INTERFACE_RESULT]
	static final String RESULT_START_ = "START";
	static final String RESULT_SUCCESS_ = "SUCCESS";
	static final String RESULT_FAIL_TRANSMIT_EMAIL_ = "FAIL_TX_EMAIL";
	static final String RESULT_FAIL_EXCEPTION_ = "FAIL_EXCEPTION";
	static final String RESULT_FAIL_OFS_FAILED_ = "FAILED"

	// Tracking error status codes [CL_TRACKING_STATUS]
	static final String TRACKING_STATUS_END_ = "END";
	static final String TRACKING_STATUS_ERROR_ = "ERROR";

	// Tracking detail type codes [CL_TRACKING_DETAIL]
	static final String TRACKING_DETAIL_INT_REJECTERR_ = "OFS_RJERR";
	static final String TRACKING_DETAIL_INT_REVIEWERR_ = "OFS_RVERR";
	static final String TRACKING_DETAIL_INT_LOG_ = "INT_LOG";

	MyInterfaceTracking(Date execDate) {
		tracking_ = new CtInterfaceTracking();
		init(execDate);
	}

	MyInterfaceTracking(CtInterfaceTracking cIntTrk, Date execDate) {
		tracking_ = cIntTrk;
		init(execDate);
	}

	void init(Date execDate) {
		tracking_ = new CtInterfaceTracking();
		tracking_.setName(INTERFACE_)
		tracking_.setType(INTERFACE_TYPE_);
		tracking_.setExecutionDate(execDate);
		tracking_.setResult(RESULT_START_);
		tracking_.saveOrUpdate();
	}

	void addTrackingDetail(String type, String status, String desc, String memo="", String contents="") {
		CtInterfaceTrackingDetail detail = new CtInterfaceTrackingDetail();
		detail.setInterfaceTracking(tracking_);
		detail.setType(type);
		detail.setStatus(status);
		detail.setDescription(desc);
		detail.setMemo(memo);
		detail.setContents(contents);
		detail.saveOrUpdate();
	}

	void addBatchDetailStatus(String status, String value, String memo="") {
		CtInterfaceBatchItemDetail batchDetail = new CtInterfaceBatchItemDetail();
		batchDetail.setInterfaceTracking(tracking_);
		batchDetail.setStatusDate(new Date());
		batchDetail.setStatus(status);  // [CL_BATCH_METRICS]
		batchDetail.setValue(value);
		if( memo != null )
			batchDetail.setMemo(memo);
		batchDetail.saveOrUpdate();
	}

	int getID() { return tracking_.id; }

	String setName(String name) {
		tracking_.setName(name);
		tracking_.saveOrUpdate();
		return name;
	}

	// Set case association / case number
	String setCase(Case cCase) {
		if (cCase != null) {
			tracking_.setCaseNumber(cCase.caseNumber);
			tracking_.setCase(cCase);
			tracking_.saveOrUpdate();
			return cCase.caseNumber;
		}
		return null;
	}

	String setCaseNumber(String caseNum, Case cCase=null) {
		tracking_.setCaseNumber(caseNum);
		if( cCase != null)
			tracking_.setCase(cCase);
		tracking_.saveOrUpdate();
		return caseNum;
	}

	void setParty(Party cParty_) {
		if( cParty_ != null )
			tracking_.setParty(cParty_)
		tracking_.saveOrUpdate();
	}

	String setMemo(String memo) {
		if( !StringUtil.isNullOrEmpty(memo) )
			tracking_.setMemo(memo);
		tracking_.saveOrUpdate();
		return memo;
	}

	String setException(String exception, String memo="") {
		tracking_.setException(exception);
		if( !StringUtil.isNullOrEmpty(memo) )
			tracking_.setMemo(memo);
		tracking_.saveOrUpdate();
		return memo + " - " + exception;
	}

	String updateResult(String result, String memo="") {
		tracking_.setResult(result); // [CL_INTERFACE_RESULT]
		if( !StringUtil.isNullOrEmpty(memo) )
			tracking_.setMemo(memo);
		tracking_.saveOrUpdate();
		return result;
	}

	void updateExternalID(String id) {
		tracking_.setExternalTrackingId(id);
		tracking_.saveOrUpdate();
	}
}

/** ------------------------------------------------------------------------------------
 * Odyssey review filing interface class
 */
public class CreateDefPtyReviewFilingInterface {
	public Boolean bDebug_ = true; 			   // debug flag used for script trace reporting
	public Script cRule_;                      // pointer to business rule for logger output
	public Party cParty_;                      // inbound document case to file
	public Date dExecDate_ = new Date();       // get current date
	public List aErrorList_ = [];        	   // validation error list
    public Boolean bReviewFilingSent_= false;  // flag to track filing condition

	// Odyssey definitions
	static final String OFS_ATTRIBUTE_TYPE_= "IOFS";	// lookupList attribute type

	// System Property attribute class
	class MySysProperties {
		String sSmbFileUsername_;
		String sSmbFilePassword_;
		String sEmailList_;
		String seSuiteEnvURL_;
	}
	public MySysProperties cSysProps_ = null;

	// OFS document status map
	Map mDocStatus_ = [
		'ofsSubmit' : 'OFSSUB',
		'ofsRecv'   : 'OFSREC',
		'ofsFiled'  : 'OFSFILED',
		'ofsReject' : 'OFSREJ',
		'ofsFailed' : 'OFSFAILED'
	];

	// ePros custom configuration class
	class EProsCfg {
		String sCaseNumber_;
        String sCaseCourtLocation_;
		String sFilingDocId_;
	}
	public EProsCfg eProsCfg_ = null;

	// Entity attributes
	MyInterfaceTracking iTracking_;        // pointer to tracking interface

	// For debug purposes
	StringBuilder sLoggerOutputBuf_ = new StringBuilder();

	/** ------------------------------------------------------------------------------------
	 * Constructor
	 * @param rule = pointer to current business rule for logger output
	 */
	CreateDefPtyReviewFilingInterface(Script rule) {
		this.cRule_ = rule
		this.cSysProps_ = new MySysProperties();
	}

	/** ------------------------------------------------------------------------------------
	 * Main interface execution handler
	 */
	public void exec(Party filingParty) {
		try {
			// Initialize interface tracking system
			logger("Script execution started @ ${dExecDate_}");
			iTracking_ = new MyInterfaceTracking(dExecDate_);

			// Get system assignments and submit filing if valid document status
			if ( assignSystemProperties() != null ) { // valid?

				logger "Searching for DEF party w/ ${mDocStatus_.ofsSubmit} status and document.id(${cRule_?._docStatus?.document?.id})";
				if (cRule_?._docStatus != null && cRule_._docStatus.statusType == mDocStatus_.ofsSubmit) { // valid OFSSUB status?
                  cRule_?._docStatus.beginDate = Timestamp.valueOf(LocalDateTime.now());
                  cRule_?._docStatus.sourceCaseNumber = "${filingParty.fullName}";

                  cRule_?._docStatus.saveOrUpdate();
					// Find submitting party w/ OFSSUB document status that matches triggering document id
					Case cCase = cRule_?._docStatus?.case; // get case
					if (cCase != null) {    // valid case?
						iTracking_.setCase(cCase);
                      	cParty_ = filingParty;
						/*cParty_ = cCase.collect("subCases.parties[partyType=='DEF']").orderBy("id").find { Party p ->
								 			   !p.subCase.collect("documents[id==#p1].statuses[statusType=='OFSSUB']", cRule_._docStatus.document?.id).empty;
						}*/
                      //cParty_ = cParty_ == null ? cCase.collect("parties[partyType=='DEF']").find({it -> it != null}): cParty_;
						// Process DEF criminal compliant filing w/ Court
						createCriminalDefPtyCourtFilingMessage(cCase);

					} else
						this.aErrorList_.add(new ValidationError(true, logger("No Case found w/ documentStatus.id(${cRule_._docStatus.id}), filing aborted")));
				} else
					logger "DocumentStatus.statusType ${cRule_?._docStatus?.statusType} != ${mDocStatus_.ofsSubmit}, filing aborted";
			}

			// Finalize script execution
			logger("Script complete");
			finalizeScriptExecution();

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::exec - Case execution handler");
			iTracking_.updateResult(MyInterfaceTracking.RESULT_FAIL_EXCEPTION_);
		}

		// Load interface result response
		cRule_._eResult= iTracking_.tracking_.result;
	}

	/** -------------------------------------------------------------------------------------------------------
	 * Assign all System Properties
	 * @return (0= Failure, 1= Successful)
	 */
	public boolean assignSystemProperties() {
		try {
			logger "Loading system properties";
			cSysProps_.seSuiteEnvURL_ = SystemProperty.getValue("general.serverUrl");
			cSysProps_.sSmbFileUsername_ = SystemProperty.getValue("interface.portal.account.username");
			cSysProps_.sSmbFilePassword_ = SystemProperty.getValue("interface.portal.account.password");
			cSysProps_.sEmailList_ = SystemProperty.getValue("interface.odyssey.emailCsvList");
		} catch ( Exception ex ){
			logger iTracking_.setException(ex.message, "Exception::assignSystemProperties - system property error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
		return true;
	}

	/** -------------------------------------------------------------------------------------------------------------------
	 * Create Criminal Court ReviewFiling Xml Message and save to disk for ESL to pickup.
	 * @param cCase - Party case
	 * @return (0= Failure, 1= Successful)
	 */
	public boolean createCriminalDefPtyCourtFilingMessage( Case cCase ) {
      boolean bRetVal= false;  // guilty until proven innocent
      String tylerCourtLocation = "fresno:cr";
	  String sVal;
	  Where cWhere;
      String tylerCaseCategory = "8";   
      String sPrimaryDocCode = getLookupListCodeAttribute("ODYSSEY_BINARY_CATEGORY_TEXT", "PRIMARY", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
      String sConnectingDocCode = getLookupListCodeAttribute("ODYSSEY_BINARY_CATEGORY_TEXT", "PRIMARY", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
      String sPhaseTypeText = getLookupListCodeAttribute("ODYSSEY_PHASE_TYPE_TEXT", "CASEFILING", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
      String sDefaultDLIssuerState= getLookupListCodeAttribute("US_STATE", "CA", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
      String crossReferenceDistrictAttorney = getLookupListCodeAttribute("ODYSSEY_IDENTIFICATION_SOURCE_TEXT", "DA", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
      String crossReferenceBooking = getLookupListCodeAttribute("ODYSSEY_IDENTIFICATION_SOURCE_TEXT", "BK", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
	  String crossReferenceCII = getLookupListCodeAttribute("ODYSSEY_IDENTIFICATION_SOURCE_TEXT", "CII", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
      logger("434: sPrimaryDocCode:${sPrimaryDocCode};sConnectingDocCode:${sConnectingDocCode};sPhaseTypeText:${sPhaseTypeText};sDefaultDLIssuerState:${sDefaultDLIssuerState};crossReferenceDistrictAttorney:${crossReferenceDistrictAttorney};crossReferenceBooking:${crossReferenceBooking};crossReferenceCII:${crossReferenceCII}");
      
		// OFS ePros configuration
		List<ChargeStatuteObj> aMissingOfsStatuteList = [];	// list to track missing ofs charge statutes
		String sCaseDocketNbr = null;

		// Create object for specific ePros configuration parameters
		this.eProsCfg_= new EProsCfg();

		try {
			// Test for valid defendant party
			if (cParty_ == null) { // invalid?
				this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("No DEF party found w/ OFSSUB status set, reviewFiling aborted")));
				return bRetVal;
			}
			logger "Found DEF filing party";
			iTracking_.setParty(cParty_);   // associate party to tracking

			// Set tracking parameters for esl service
			
            OtherCaseNumber cOldCaseFiling = cCase.collect("otherCaseNumbers[type=='CRT' && memo != null && memo.contains(#p1) && memo.contains(#p2)]", "${cParty_.firstName}".toString(),  "${cParty_.lastName}".toString()).orderBy("lastUpdated").last();
          logger("cOldCaseFiling:${cOldCaseFiling}");
          eProsCfg_.sCaseNumber_ = cOldCaseFiling?.number;
          logger("435:eProsCfg_.sCaseNumber_:${eProsCfg_.sCaseNumber_};cOldCaseFiling:${cOldCaseFiling?.number}");
            eProsCfg_.sCaseCourtLocation_ =  tylerCourtLocation;
			// Initialize document statuses to check for valid 'OFSSUB' status and cleanup older statuses
			if (!initializeDocumentStatus(cParty_)) { // invalid 'OFSSUB' document?
				this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("No DEF documents found w/ ${mDocStatus_.ofsSubmit} status set, reviewFiling aborted")));
				return bRetVal;
			}

			logger("Building court reviewFiling <XML> message for DEF party (${cParty_.person.lastName}, ${cParty_.person.firstName}) - case#(${cCase.caseNumber})");

			// Construct xml case message and queue to to file
			StringBuilder OFS_CaseXml = new StringBuilder();

			OFS_CaseXml.append('<ReviewFilingRequestMessage xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
					+ 'xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
					+ 'xmlns:u="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" '
					+ 'xmlns="urn:oasis:names:tc:legalxml-courtfiling:wsdl:WebServicesProfile-Definitions-4.0">');

			OFS_CaseXml.append('<CoreFilingMessage xsi:schemaLocation="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:CoreFilingMessage-4.0 ..\\..\\..\\Schema\\message\\ECF-4.0-CoreFilingMessage.xsd" '
					+ 'xmlns="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:CoreFilingMessage-4.0" '
					+ 'xmlns:cext="urn:tyler:ecf:extensions:Criminal" xmlns:j="http://niem.gov/niem/domains/jxdm/4.0" '
					+ 'xmlns:ecf="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:CommonTypes-4.0" '
					+ 'xmlns:tyler="urn:tyler:ecf:extensions:Common" xmlns:nc="http://niem.gov/niem/niem-core/2.0" '
					+ 'xmlns:s="http://niem.gov/niem/structures/2.0" '
					+ 'xmlns:criminal="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:CriminalCase-4.0">');

			// Get/Report Case cross reference number
          if(cRule_._submitCaseCrossReference == true){
			OFS_CaseXml.append('<nc:DocumentIdentification>');
			OFS_CaseXml.append("<nc:IdentificationID>${cCase.caseNumber}</nc:IdentificationID>");
			OFS_CaseXml.append('<nc:IdentificationCategoryText>CaseCrossReferenceNumber</nc:IdentificationCategoryText>');
			//cross reference production
            OFS_CaseXml.append("<nc:IdentificationSourceText>${crossReferenceDistrictAttorney}</nc:IdentificationSourceText>");
            //cross reference staging
            //OFS_CaseXml.append('<nc:IdentificationSourceText>85996</nc:IdentificationSourceText>');
			OFS_CaseXml.append('</nc:DocumentIdentification>');
        }

			// Get/Report SO (sheriff officers) cross reference number
			Identification cSONbr = cParty_.collect("person.identifications[identificationType=='CII' and (effectiveTo == null or #p1 < effectiveTo) and (effectiveFrom == null or #p2 > effectiveFrom) and (status == null or status == 'VAL')]", new Date(), new Date()).last();
			if (cSONbr != null) {
				logger("SO CII reference = ${cSONbr?.identificationNumber}")
				OFS_CaseXml.append('<nc:DocumentIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cSONbr?.identificationNumber)}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>CaseCrossReferenceNumber</nc:IdentificationCategoryText>');
			  //production
                OFS_CaseXml.append("<nc:IdentificationSourceText>${crossReferenceCII}</nc:IdentificationSourceText>");
              //staging	
              //OFS_CaseXml.append("<nc:IdentificationSourceText>86001</nc:IdentificationSourceText>");
				OFS_CaseXml.append('</nc:DocumentIdentification>');
			} else
				logger("No valid party.person SO CII identification found on party");

			// Get/Report Arrest Booking Number cross reference
			Arrest cArrestBookingNbr = cParty_.collect("arrests[bookingNumber!=null]").last();
			// use most current w/ valid bookingNbr
			if (cArrestBookingNbr != null) {
				logger("Booking number reference = ${cArrestBookingNbr?.bookingNumber}")
				OFS_CaseXml.append('<nc:DocumentIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cArrestBookingNbr?.bookingNumber)}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>CaseCrossReferenceNumber</nc:IdentificationCategoryText>');
			  //production
                OFS_CaseXml.append("<nc:IdentificationSourceText>${crossReferenceBooking}</nc:IdentificationSourceText>");
              //staging	
              //OFS_CaseXml.append('<nc:IdentificationSourceText>85992</nc:IdentificationSourceText>');
				OFS_CaseXml.append('</nc:DocumentIdentification>');
			} else
				logger("No valid arrest booking number found on party");

			OFS_CaseXml.append('<ecf:SendingMDELocationID>');
			OFS_CaseXml.append('<nc:IdentificationID>http://localhost:8000</nc:IdentificationID>');
			OFS_CaseXml.append('</ecf:SendingMDELocationID>');
			OFS_CaseXml.append('<ecf:SendingMDEProfileCode>urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:WebServicesMessaging-2.0</ecf:SendingMDEProfileCode>');
			OFS_CaseXml.append('<criminal:CriminalCase xsi:schemaLocation="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:CriminalCase-4.0 ..\\..\\..\\Schema\\casetype\\ECF-4.0-CriminalCase.xsd">');
			OFS_CaseXml.append('<nc:CaseCategoryText>8</nc:CaseCategoryText>');

			OFS_CaseXml.append('<j:CaseAugmentation>');
			OFS_CaseXml.append('<j:CaseCourt>');
			OFS_CaseXml.append('<nc:OrganizationIdentification>');
			OFS_CaseXml.append("<nc:IdentificationID>${tylerCourtLocation}</nc:IdentificationID>");
			OFS_CaseXml.append('</nc:OrganizationIdentification>');
			OFS_CaseXml.append('</j:CaseCourt>');

			 /**
             * Logic to handle court case trackingID for case subsequent filings
             * 1) CaseTrackingID to it in ePros
             * (Case.otherCaseNumbers[type="CRT"]), look to see if it has a cf_OFSCaseTrackingID value. If it does, use
             * that value in the message for <nc:CaseTrackingID>. If it doesn't check to make sure there's caseDocketId
             * available in the Case.otherCaseNumbers.number field, if not reject the filing otherwise proceed to next step.
             *
             * 2) If there's no cf_OFSCaseTrackingID value on the OtherCaseNumber of type CRT, before the final message
             * is compiled and sent to OFS, query the GetCaseList endpoint in the eslService using the following parameters:
             * SendingMDELocationID.IdentificationID -> Notification callback URL inserted by service
             * PersonOtherIdentification.IdentificationID -> e8cacd46-ca46-411d-bbbe-b8a54ee432c4
             * CaseCourt -> fresno:cr
             * CaseDocketID -> Case.otherCaseNumbers[type="CRT"].number
             *
             * Take the CaseTrackingID where the CaseDocketID is the Case.otherCaseNumbers[type="CRT"].number and the
             * CaseCourt is fresno:cr and use that as the value for <nc:CaseTrackingID> in the message, finalize it,
             * save it to disk and then send it to OFS system.
             */
            cOldCaseFiling = cCase.collect("otherCaseNumbers[type=='CRT' && memo != null && memo.contains(#p1) && memo.contains(#p2)]", "${cParty_.firstName}".toString(),  "${cParty_.lastName}".toString()).orderBy("lastUpdated").last();
          logger("cOldCaseFiling:${cOldCaseFiling}");
            if (cOldCaseFiling != null) { // valid court#?
                logger("Court CaseDocketNbr(${cOldCaseFiling?.number}) found for subsequent filing");

                // If 'CRT' type court#, look for a valid caseTrackingID, if not found, but caseDocketID valid, use in service to query caseDocketID from Odyssey EFM
                if (StringUtil.isNullOrEmpty(cOldCaseFiling?.cf_OFSCaseTrackingID)) {  // invalid caseDocketID?
                    logger("Court cf_OFSCaseTrackingID not found, adding CaseDocketNbr to eProCfg for eslService to query from OFS");
                    if (!StringUtil.isNullOrEmpty(cOldCaseFiling?.number)) {  // must have valid caseDocketID?
                        sCaseDocketNbr= cOldCaseFiling.number;     // assign docket number to use for esl_service trackingID OFS query
                    }else{
                        this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("OtherCaseNumber 'CRT' type found with no caseDocketNumber assigned, rejected")));
                        return false;
                    }
                } else
                    logger("Court cf_OFSCaseTrackingID(${cOldCaseFiling.cf_OFSCaseTrackingID}) found, add to filing");
            }
           
            // Insert CaseTrackingID for subsequent filing, if not found, just add placed holder for OFS service to locate if a valid caseDocketID is provided
            OFS_CaseXml.append('<j:CaseLineageCase>');
            //OFS_CaseXml.append("<nc:CaseTrackingID>${xmlStrUtil(cOldCaseFiling?.cf_OFSCaseTrackingID)}</nc:CaseTrackingID>");
          	OFS_CaseXml.append("<nc:CaseTrackingID>");
          	OFS_CaseXml.append("${xmlStrUtil(cOldCaseFiling?.cf_OFSCaseTrackingID)}");
          	OFS_CaseXml.append("</nc:CaseTrackingID>");
            OFS_CaseXml.append('</j:CaseLineageCase>')

			OFS_CaseXml.append('</j:CaseAugmentation>');
          String caseCourtNumber = !cCase.collect("otherCaseNumbers[type=='CRT']").isEmpty() ? cCase.collect("otherCaseNumbers[type=='CRT']")?.orderBy("lastUpdated")?.find({thisNumber -> thisNumber != null && thisNumber.number !=null})?.number : "";
          
			OFS_CaseXml.append('<tyler:CaseAugmentation xsi:schemaLocation="urn:tyler:ecf:extensions:Common ..\\..\\..\\Schema\\Substitution\\Tyler.xsd">');
          cOldCaseFiling = cCase.collect("otherCaseNumbers[type=='CRT' && memo != null && memo.contains(#p1) && memo.contains(#p2)]", "${cParty_.firstName}".toString(),  "${cParty_.lastName}".toString()).orderBy("lastUpdated").last();
          logger("cOldCaseFiling:${cOldCaseFiling}");
            if(cOldCaseFiling == null){
			// Add case assignment role for attorney
			String sAttorneyRef = "ATTY";
          if (cRule_._submitAttorneyParticipant == true){
			OFS_CaseXml.append('<ecf:CaseOtherEntityAttorney>');
			OFS_CaseXml.append("<nc:RoleOfPersonReference s:ref=\"${xmlStrUtil(sAttorneyRef, "Attorney1")}\"/>");
			OFS_CaseXml.append('<j:CaseOfficialRoleText>LEAD</j:CaseOfficialRoleText>');
			OFS_CaseXml.append("<ecf:CaseRepresentedPartyReference s:ref=\"${xmlStrUtil(cParty_.partyType, "Party1")}\"/>");
			OFS_CaseXml.append('</ecf:CaseOtherEntityAttorney>');

			OFS_CaseXml.append('<ecf:CaseParticipant>');
			OFS_CaseXml.append("<ecf:EntityPerson s:id=\"${xmlStrUtil(sAttorneyRef, "Attorney1")}\">");

			// Add unique OFS attorney ID
			//logger("Assigning default staging ofs attorneyID(${cRule_._ofsUniqueAttorneyId})");
            logger("Assigning default production ofs attorneyID(${cRule_._ofsUniqueAttorneyIdProduction})");
			OFS_CaseXml.append('<nc:PersonOtherIdentification>');
            if (cRule_._submitStaticAttorney == true && cRule_._fileToStaging == true){
	          OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cRule_._ofsUniqueAttorneyId)}</nc:IdentificationID>");
            } else if (cRule_._submitStaticAttorney == true && cRule_._fileToStaging == false){
              OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cRule_._ofsUniqueAttorneyIdProduction)}</nc:IdentificationID>");
            } else{
              OFS_CaseXml.append("<nc:IdentificationID></nc:IdentificationID>");
            }
			OFS_CaseXml.append("<nc:IdentificationCategoryText>ATTORNEYID</nc:IdentificationCategoryText>");
			OFS_CaseXml.append('</nc:PersonOtherIdentification>');
			OFS_CaseXml.append('</ecf:EntityPerson>');
			//OFS_CaseXml.append("<ecf:CaseParticipantRoleCode>${xmlStrUtil(getLookupListCodeAttribute("ODYSSEY_CASE_PARTICIPANT_ROLE", sAttorneyRef, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</ecf:CaseParticipantRoleCode>");
            OFS_CaseXml.append("<ecf:CaseParticipantRoleCode>ATTY</ecf:CaseParticipantRoleCode>");
			OFS_CaseXml.append('</ecf:CaseParticipant>');
          }
			OFS_CaseXml.append('<ecf:CaseParticipant>');

			// Add DEF party information
			OFS_CaseXml.append("<ecf:EntityPerson s:id=\"${xmlStrUtil(cParty_.partyType, "Party1")}\">");

			// Add party/person/profile information
			PersonProfile cProfile = cParty_.collect("person.profiles").last();
			if( cProfile?.dateOfBirth != null ) {
				sVal = convDateFmtToStr(cProfile?.dateOfBirth, "yyyy-MM-dd");
				logger("Adding DOB(${xmlStrUtil(sVal)})");
				OFS_CaseXml.append('<nc:PersonBirthDate>');
				OFS_CaseXml.append("<nc:Date>${xmlStrUtil(sVal)}</nc:Date>");
				OFS_CaseXml.append('</nc:PersonBirthDate>');
			}

			if( !StringUtil.isNullOrEmpty((String)cProfile?.citizenship) ) {
				logger("Adding citizenship(${xmlStrUtil(cProfile?.citizenship)})");
				OFS_CaseXml.append("<nc:PersonCitizenshipFIPS10-4Code>${xmlStrUtil(getLookupListCodeAttribute("COUNTRY", cProfile?.citizenship, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonCitizenshipFIPS10-4Code>");
			}

			if( !StringUtil.isNullOrEmpty((String)cProfile?.ethnicity) ) {
				logger("Adding ethnicity(${xmlStrUtil(cProfile?.ethnicity)})");
				OFS_CaseXml.append("<nc:PersonEthnicityText>${xmlStrUtil(getLookupListCodeAttribute("ETHNICITY", cProfile?.ethnicity, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonEthnicityText>");
			}
			
			if( !StringUtil.isNullOrEmpty((String)cProfile?.eyeColor) ) {
				logger("Adding eyeColor(${xmlStrUtil(cProfile?.eyeColor)})");
				OFS_CaseXml.append("<nc:PersonEyeColorCode>${xmlStrUtil(getLookupListCodeAttribute("EYE_COLOR", cProfile?.eyeColor, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonEyeColorCode>");
			}
			
			if( !StringUtil.isNullOrEmpty((String)cProfile?.hairColor) ) {
				logger("Adding hairColor(${xmlStrUtil(cProfile?.hairColor)})");
				OFS_CaseXml.append("<nc:PersonHairColorCode>${xmlStrUtil(getLookupListCodeAttribute("HAIR_COLOR", cProfile?.hairColor, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonHairColorCode>");
			}

			if( !StringUtil.isNullOrEmpty((String)cProfile?.height) ) {
				OFS_CaseXml.append('<nc:PersonHeightMeasure>');
				sVal = convertFtInFmtToInches(cProfile?.height);
				OFS_CaseXml.append("<nc:MeasureText>${xmlStrUtil(sVal)}</nc:MeasureText>");
				OFS_CaseXml.append('<nc:LengthUnitCode>INH</nc:LengthUnitCode>');    // default=inches
				OFS_CaseXml.append('</nc:PersonHeightMeasure>');
			}

			if( cProfile != null && cProfile?.primaryLanguage == 'ENGL' )
				OFS_CaseXml.append("<nc:PersonLanguageEnglishIndicator>${((cProfile?.primaryLanguage == 'ENGL') ? 'true' : 'false')}</nc:PersonLanguageEnglishIndicator>");

			logger("Adding DEF name - ${xmlStrUtil(cParty_.person?.lastName)}, ${xmlStrUtil(cParty_.person?.middleName)} ${xmlStrUtil(cParty_.person?.firstName)} ${xmlStrUtil(cParty_.person?.nameSuffix)}");
			OFS_CaseXml.append('<nc:PersonName>');
			OFS_CaseXml.append("<nc:PersonGivenName>${xmlStrUtil(cParty_.person?.firstName)}</nc:PersonGivenName>");
			OFS_CaseXml.append("<nc:PersonMiddleName>${xmlStrUtil(cParty_.person?.middleName)}</nc:PersonMiddleName>");
			OFS_CaseXml.append("<nc:PersonSurName>${xmlStrUtil(cParty_.person?.lastName)}</nc:PersonSurName>");
			OFS_CaseXml.append("<nc:PersonNameSuffixText>${xmlStrUtil(getLookupListCodeAttribute("NAME_SUFFIX", cParty_.person?.nameSuffix, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonNameSuffixText>");
			OFS_CaseXml.append('</nc:PersonName>');

			// Add SSN identification
			Identification cSSNId = cParty_.collect("person.identifications[identificationType=='SSN' and (effectiveTo == null or #p1 < effectiveTo) and (effectiveFrom == null or #p2 > effectiveFrom) and (status == null or status == 'VAL')]", new Date(), new Date()).last();
          if (cRule_._submitSSN == true){	
          if (cSSNId != null && cSSNId.identificationNumber != null && !cSSNId.identificationNumber.isEmpty() && cSSNId?.identificationNumber.trim().replaceAll("\\D","").length() == 9) { // valid?
                String ssn = cSSNId?.identificationNumber.trim().replaceAll("\\D","");
                ssn = cRule_._formatSSN == true ? "${ssn.substring(0,3)}-${ssn.substring(3,5)}-${ssn.substring(5)}" : ssn;
            logger("Adding SSN# - ${ssn}");
				OFS_CaseXml.append('<nc:PersonOtherIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(ssn)}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>SSN</nc:IdentificationCategoryText>');
				OFS_CaseXml.append('</nc:PersonOtherIdentification>');
			}
          }
			// Add FBI identification
			Identification cFBIId = cParty_.collect("person.identifications[identificationType=='FBI' and (effectiveTo == null or #p1 < effectiveTo) and (effectiveFrom == null or #p2 > effectiveFrom) and (status == null or status == 'VAL')]", new Date(), new Date()).last();
			if (cFBIId != null) {
				logger("Adding FBI# - ${xmlStrUtil(cFBIId?.identificationNumber)}");
				OFS_CaseXml.append('<nc:PersonOtherIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cFBIId?.identificationNumber)}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>FBI</nc:IdentificationCategoryText>');
				OFS_CaseXml.append('</nc:PersonOtherIdentification>');
			}

			// Add person primary language. If languageCode attribute is not found, trigger an assignment error and don't post PrimaryLanguage section
			logger("Primary langauge - ${xmlStrUtil(cProfile?.primaryLanguage)}");
			String sLangCode = getLookupListCodeAttribute("LANGUAGE", cProfile?.primaryLanguage, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
			if (!StringUtil.isNullOrEmpty(sLangCode) ) { // valid attribute code?
				if( sLangCode?.toLowerCase() != 'eng' ) {
					OFS_CaseXml.append('<nc:PersonPrimaryLanguage>');
					OFS_CaseXml.append("<nc:LanguageCode>${xmlStrUtil(sLangCode?.toLowerCase())}</nc:LanguageCode>");
					OFS_CaseXml.append('</nc:PersonPrimaryLanguage>');
				}
			} else {
				if (!StringUtil.isNullOrEmpty(cProfile?.primaryLanguage)) // only post warning if primaryLanguage w/o an associated attribute code
					this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("No [${OFS_ATTRIBUTE_TYPE_}] attribute code found for languageCode(${cProfile?.primaryLanguage}) in [LANGUAGE] lookupList")));
			}

			// Add person profile information
			logger("Race(${xmlStrUtil(cProfile?.race)}) / Gender(${xmlStrUtil(cProfile?.gender)}) / Weight(${xmlStrUtil(cProfile?.weight?.toString())})");
         
          if(cRule_._submitRace == true && !StringUtil.isNullOrEmpty((String)cProfile?.race) ){
				OFS_CaseXml.append("<nc:PersonRaceText>${xmlStrUtil(getLookupListCodeAttribute("RACE", cProfile?.race, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonRaceText>");
          }
                //OFS_CaseXml.append("<nc:PersonRaceText>W</nc:PersonRaceText>");

              if(cRule_._submitPersonSexCode == true && !StringUtil.isNullOrEmpty((String)cProfile?.gender) ){
				OFS_CaseXml.append("<nc:PersonSexCode>${xmlStrUtil(getLookupListCodeAttribute("GENDER", cProfile?.gender, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:PersonSexCode>");
              }

			if( !StringUtil.isNullOrEmpty((String)cProfile?.weight?.toString()) ) {
				OFS_CaseXml.append('<nc:PersonWeightMeasure>');
				OFS_CaseXml.append("<nc:MeasureText>${xmlStrUtil(cProfile?.weight?.toString())}</nc:MeasureText>");
				OFS_CaseXml.append('<nc:WeightUnitCode>LBR</nc:WeightUnitCode>');
				OFS_CaseXml.append('</nc:PersonWeightMeasure>');
			}

			OFS_CaseXml.append('<ecf:PersonAugmentation>');

			// Add drivers license information
			Identification cDLId = cParty_.collect("person.identifications[identificationType=='CDL' and (effectiveTo == null or #p1 < effectiveTo) and (effectiveFrom == null or #p2 > effectiveFrom) and (status == null or status == 'VAL')]", new Date(), new Date()).last();
			if (cDLId != null) {
				logger("Adding DL(${cDLId?.identificationNumber})/Type(${cDLId?.identificationType})/State(" +
						StringUtil.isNullOrEmpty(cDLId?.issuerState)? sDefaultDLIssuerState: cDLId?.issuerState + ")");
				OFS_CaseXml.append('<ecf:PersonDriverLicense>');
				OFS_CaseXml.append('<nc:DriverLicenseIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cDLId?.identificationNumber)}</nc:IdentificationID>");
				OFS_CaseXml.append("<nc:IdentificationCategoryText>${xmlStrUtil(getLookupListCodeAttribute("IDENTIFICATION_TYPE", cDLId?.identificationType, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:IdentificationCategoryText>");
				OFS_CaseXml.append("<j:DrivingJurisdictionAuthorityNCICLSTACode>${xmlStrUtil(StringUtil.isNullOrEmpty(cDLId?.issuerState)? sDefaultDLIssuerState: cDLId?.issuerState)}</j:DrivingJurisdictionAuthorityNCICLSTACode>");
				OFS_CaseXml.append('</nc:DriverLicenseIdentification>');
				OFS_CaseXml.append('</ecf:PersonDriverLicense>');
			} else
				logger("No defendant DL found");

			// Add party address
			Address cAdr = cParty_.collect("person.addresses[addressType == 'M' and effectiveNow]").last();
			if (cAdr != null) { // valid?
				logger("Adding Address(${cAdr?.address1}, ${cAdr?.city} ${cAdr?.state} ${cAdr?.zip})");
				OFS_CaseXml.append('<nc:ContactInformation>');
				OFS_CaseXml.append('<nc:ContactMailingAddress>');
				OFS_CaseXml.append('<nc:StructuredAddress>');
				OFS_CaseXml.append('<nc:LocationStreet>');
				OFS_CaseXml.append("<nc:StreetFullText>${xmlStrUtil(cAdr?.address1)}</nc:StreetFullText>");
				OFS_CaseXml.append('</nc:LocationStreet>');
				OFS_CaseXml.append("<nc:AddressSecondaryUnitText>${xmlStrUtil(cAdr?.address2)}</nc:AddressSecondaryUnitText>");
				OFS_CaseXml.append("<nc:LocationCityName>${xmlStrUtil(cAdr?.city)}</nc:LocationCityName>");
				OFS_CaseXml.append("<nc:LocationStateName>${xmlStrUtil(cAdr?.state)}</nc:LocationStateName>");
				OFS_CaseXml.append("<nc:LocationCountryFIPS10-4Code>${xmlStrUtil(getLookupListCodeAttribute("ADDRESS_COUNTRY", cAdr?.country, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</nc:LocationCountryFIPS10-4Code>");
				OFS_CaseXml.append("<nc:LocationPostalCode>${xmlStrUtil(cAdr?.zip)}</nc:LocationPostalCode>");
				OFS_CaseXml.append('</nc:StructuredAddress>');
				OFS_CaseXml.append('</nc:ContactMailingAddress>');
				OFS_CaseXml.append('</nc:ContactInformation>');
			} else
				logger("No defendant contact address found");

			OFS_CaseXml.append('</ecf:PersonAugmentation>');
			OFS_CaseXml.append('</ecf:EntityPerson>');

			// Add case type information
			logger("Adding caseType(${xmlStrUtil(cCase.caseType)})");
			OFS_CaseXml.append("<ecf:CaseParticipantRoleCode>${xmlStrUtil(getLookupListCodeAttribute("ODYSSEY_CASE_PARTICIPANT_ROLE", cParty_.partyType, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</ecf:CaseParticipantRoleCode>");
			OFS_CaseXml.append('</ecf:CaseParticipant>');
			OFS_CaseXml.append("<tyler:CaseTypeText>${xmlStrUtil(getLookupListCodeAttribute("CASE_TYPE", cCase.caseType, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</tyler:CaseTypeText>");
			OFS_CaseXml.append('<tyler:AttachServiceContactIndicator>false</tyler:AttachServiceContactIndicator>');
            }
			OFS_CaseXml.append('</tyler:CaseAugmentation>');
      
			// Add arrest information
			Arrest cArst = cParty_.collect("arrests").last();
          if (cArst != null && cArst.arrestDate != null){
			OFS_CaseXml.append('<criminal:CaseArrest>');
			OFS_CaseXml.append('<nc:ActivityDate>');
			sVal = toISO8601UTC(combineDateTime(cArst?.arrestDate,cArst?.arrestTime));
			logger("Adding arrestDate(${xmlStrUtil(sVal)})");
			OFS_CaseXml.append("<nc:DateTime>${xmlStrUtil(sVal)}</nc:DateTime>");
			OFS_CaseXml.append('</nc:ActivityDate>');

			// Add offender booking information
			Ce_ParticipantReportNumbers cRptArrestNbr = cCase.collect("ce_Participants.ce_ParticipantReportNumbers[type=='LAG' && number != null]").last();
			if ( cRptArrestNbr != null ) {
				logger("Adding arrest#(${xmlStrUtil(cRptArrestNbr.number)})");
				OFS_CaseXml.append('<j:ArrestAgencyRecordIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cRptArrestNbr.number)}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>ARREST_NUMBER</nc:IdentificationCategoryText>');
				OFS_CaseXml.append('</j:ArrestAgencyRecordIdentification>');
			} else
				logger("No defendant arresting number found");

			// Find arrest bail amount from charge statute
			String sArstBailRec = "Unspecified Bail"; // set default msg if not bail amount
			StatuteFine cChgBailSt = cParty_.collect("charges.statute.statuteFines[baseAmount > 0]").last();
			if ( cChgBailSt != null ) { // valid?
				sArstBailRec = cChgBailSt.baseAmount?.toString();  // set base amount
				logger "Adding Arrest Bail Recommendation ($sArstBailRec)";
			}
			OFS_CaseXml.append("<j:ArrestBailRecommendationText>${xmlStrUtil(sArstBailRec)}</j:ArrestBailRecommendationText>");

			// Add arrest charge information
			OFS_CaseXml.append('<j:ArrestLocation>');
			OFS_CaseXml.append("<nc:LocationName>${xmlStrUtil(cArst?.odysseyLocation)}</nc:LocationName>");
			OFS_CaseXml.append('</j:ArrestLocation>');

			// Attempt to locate arrest LEO participant w/ valid Badge number
			OFS_CaseXml.append('<j:ArrestOfficial>');

			Identification cArstLeoId = cCase.collect("ce_Participants[type=='LEO' and subType=='SUBMOFCR'].person.identifications[identificationType=='OFCID' and identificationNumber != null]").last();
			if( cArstLeoId != null ) { // valid arresting LEO
				logger("Adding Arresting LEO Badge#(${cArstLeoId?.identificationNumber}) for ${cArstLeoId.person?.lastName}, ${cArstLeoId.person?.firstName}");
				OFS_CaseXml.append('<j:EnforcementOfficialBadgeIdentification>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cArstLeoId?.identificationNumber)}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>BADGE_NUMBER</nc:IdentificationCategoryText>');
				OFS_CaseXml.append('</j:EnforcementOfficialBadgeIdentification>');
			}

			// Attempt to locate arresting organization identifier
			String sArstOrgID= null;
			Identification cArstOrdId = cCase.collect("ce_Participants[type=='AGENCY' and subType=='ARRAGENCY'].person.identifications[identificationType=='ODSYAGC' and identificationNumber != null]").last();
			if( cArstOrdId != null ) { // valid ID
				sArstOrgID = cArstOrdId?.identificationNumber;
				logger("Adding arrest organization identification $sArstOrgID");
			}
			OFS_CaseXml.append('<j:EnforcementOfficialUnit>');
			OFS_CaseXml.append('<nc:OrganizationIdentification>');
			OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(sArstOrgID)}</nc:IdentificationID>");
			OFS_CaseXml.append('</nc:OrganizationIdentification>');
			OFS_CaseXml.append('</j:EnforcementOfficialUnit>');
			OFS_CaseXml.append('</j:ArrestOfficial>');

			OFS_CaseXml.append('</criminal:CaseArrest>');
        }
		//String caseCourtNumber = !cCase.collect("otherCaseNumbers[type=='CRT']").isEmpty() ? cCase.collect("otherCaseNumbers[type=='CRT']")?.orderBy("lastUpdated")?.find({thisNumber -> thisNumber != null && thisNumber.number !=null})?.number : "";
          cOldCaseFiling = cCase.collect("otherCaseNumbers[type=='CRT' && memo != null && memo.contains(#p1) && memo.contains(#p2)]", "${cParty_.firstName}".toString(),  "${cParty_.lastName}".toString()).orderBy("lastUpdated").last();
          logger("cOldCaseFiling:${cOldCaseFiling}");
          if (cOldCaseFiling == null){
			if (cParty_.charges != null) {    // charges?
				logger("Adding ${cParty_.charges.size()} charge(s)");

				// Gather up all charges
				List<Charge> lChgs = cParty_.collect("charges[statute != null]");
				for( Charge c in lChgs ) {
					OFS_CaseXml.append('<cext:Charge xmlns:cext="urn:tyler:ecf:extensions:Criminal" xsi:schemaLocation="urn:tyler:ecf:extensions:Criminal ..\\..\\..\\Schema\\Substitution\\CriminalExtensions.xsd">');

					logger("Adding chargeNbr(${xmlStrUtil(c.chargeNumber)})");
					OFS_CaseXml.append('<j:ChargeSequenceID>');
					OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(c.chargeNumber)}</nc:IdentificationID>");
					OFS_CaseXml.append('</j:ChargeSequenceID>');

					/**
					 * The Odyssey system will only except OFS style charge statute codes, so if the cf_ofsCode field is blank
					 * we will queue up the ePros statute/attributes so the ESL service can query this information from
					 * the Odyssey server and later update the ePros environment.
					 */
					if (c.statute != null) {    // valid statute?
						if (c.statute.cf_ofsCode == null) { // Ofs statute blank?
							List<String> lAttributes = getOfsChgStatuteAttributes(c);
							aMissingOfsStatuteList.add(new ChargeStatuteObj(c.chargeNumber, c.statute.sectionNumber, c.statute.sectionName, lAttributes));
							// cue revised sectionNbr for ELS
							logger("cf_ofsCode is blank, adding statute sectionNumber(${c.statute.sectionNumber}), sectionName(${c.statute.sectionName}) and sectionCode(${c.statute.sectionCode}) to ESL que")
						} else
							logger("Adding cf_ofsCode(${c.statute.cf_ofsCode})");
					} else
						this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("Charge#($c.chargeNumber) has no statute assigned")));

					// Add charge statute information if available
					if (c.statute != null) {
                      RichList statuteLanguageChoices;
                      if (!c.collect("chargeAttributes").isEmpty()){
                      statuteLanguageChoices = c.statute.collect("statuteLanguageChoices[odysseyAttributes == #p1]", c.collect("chargeAttributes")?.first());
                      }
						OFS_CaseXml.append('<cext:ChargeStatute>');

						// Override statute w/ generic BR input statute if available else use mapped statute value
						String sStatuteOfsCode = xmlStrUtil(c.statute.cf_ofsCode); // set default
                      if (cRule_?._fileToStaging == true){
                        sStatuteOfsCode = com.sustain.rule.model.RuleDef.exec("INTERFACE_OFS_UPDATE_FILING_CODES_LOCAL_XML", null, ["lookuplist": "STATUTE", "casecategory": "", "name": "${c.statute.sectionName}:${c.statute.sectionNumber}".toString(), "filingcodeid": ""]).getOutputValue("code");
                      }
						if( !StringUtil.isNullOrEmpty((String)cRule_?._ofsGenericStatuteCode) ) {  // available, use generic value?
							sStatuteOfsCode = cRule_._ofsGenericStatuteCode; // set generic value
							logger("Statute cf_ofsCode overridden w/ $sStatuteOfsCode");
						}

						OFS_CaseXml.append('<j:StatuteCodeIdentification>');
                      if (!c.collect("chargeAttributes").isEmpty() && statuteLanguageChoices != null && !statuteLanguageChoices.isEmpty()){
                        logger("856:StatuteCodeIdentification:A");
                        OFS_CaseXml.append("<nc:IdentificationID>${statuteLanguageChoices?.first()?.odysseyCode}</nc:IdentificationID>");
                        OFS_CaseXml.append('</j:StatuteCodeIdentification>');
                        OFS_CaseXml.append("<j:StatuteDescriptionText>${statuteLanguageChoices?.first()?.memo}</j:StatuteDescriptionText>");
                        OFS_CaseXml.append("<j:StatuteLevelText>${statuteLanguageChoices?.first()?.odysseyLevelCode}</j:StatuteLevelText>");
                        OFS_CaseXml.append("<j:StatuteOffenseIdentification>");
						//OFS_CaseXml.append("<nc:IdentificationID>${statuteLanguageChoices?.first()?.choice}</nc:IdentificationID>");
                        OFS_CaseXml.append("<nc:IdentificationID>${statuteLanguageChoices?.first()?.statute?.source}</nc:IdentificationID>");
						OFS_CaseXml.append("</j:StatuteOffenseIdentification>");
                      } else if (!c.collect("chargeAttributes").isEmpty() && (statuteLanguageChoices == null || statuteLanguageChoices.isEmpty())){
                        logger("856:StatuteCodeIdentification:B");
                        OFS_CaseXml.append("<nc:IdentificationID>74763</nc:IdentificationID>");
                        OFS_CaseXml.append('</j:StatuteCodeIdentification>');
                        OFS_CaseXml.append("<j:StatuteDescriptionText>E-Filing Charge</j:StatuteDescriptionText>");
                        OFS_CaseXml.append("<j:StatuteLevelText>80755</j:StatuteLevelText>");
                        OFS_CaseXml.append("<j:StatuteOffenseIdentification>");
						OFS_CaseXml.append("<nc:IdentificationID>123(a)</nc:IdentificationID>");
						OFS_CaseXml.append("</j:StatuteOffenseIdentification>");
                      }else{
                        logger("856:StatuteCodeIdentification:C");
						OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(sStatuteOfsCode)}</nc:IdentificationID>");
						OFS_CaseXml.append('</j:StatuteCodeIdentification>');
						OFS_CaseXml.append("<j:StatuteDescriptionText>${xmlStrUtil(c.statute.sectionName)}</j:StatuteDescriptionText>");
                        OFS_CaseXml.append("<j:StatuteLevelText>${xmlStrUtil(getLookupListCodeAttribute("STATUTE_SUBCATEGORY", c.statute.subcategory, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US"))}</j:StatuteLevelText>");
                        OFS_CaseXml.append("<j:StatuteOffenseIdentification>");
						OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(c.statute.source)}</nc:IdentificationID>");
						OFS_CaseXml.append("</j:StatuteOffenseIdentification>");
                      }
						OFS_CaseXml.append('</cext:ChargeStatute>');
					} else
						logger("No defendant charge statute");

					OFS_CaseXml.append('<criminal:ChargeOffense>');

					// Add charge date
					sVal = toISO8601UTC(c.chargeDate);
					logger("Adding chargeDate(${xmlStrUtil(sVal)})");
					OFS_CaseXml.append('<nc:ActivityDate>');
					OFS_CaseXml.append("<nc:DateTime>${xmlStrUtil(sVal)}</nc:DateTime>");
					OFS_CaseXml.append('</nc:ActivityDate>');

					OFS_CaseXml.append('</criminal:ChargeOffense>');
					OFS_CaseXml.append("<criminal:ChargeAmendedIndicator>${(c.status == 'AMEND') ? "true" : "false"}</criminal:ChargeAmendedIndicator>");

					/**
					 * The Odyssey system will only except OFS style subCharge statute codes, so if the cf_ofsCode field
					 * is blank we will queue up the ePros statute/attributes so the ESL service can query this information
					 * from the Odyssey server and later update the ePros environment.
					 * (Only 1 subCharge is allowed per charge for OFS)
					 */
					StatuteAction cSubChgStatuteAct = null;
					SubCharge cSubChg = c.collect("subCharges").first(); // use the first one
					if (cSubChg != null && cSubChg.statute != null) {
						cSubChgStatuteAct = cSubChg.collect("statute.statuteActions").first();
						if (cSubChg.statute.cf_ofsCode == null) {
							aMissingOfsStatuteList.add(new ChargeStatuteObj(cSubChg.chargeNumber, cSubChg.statute.sectionNumber, cSubChg.statute.sectionName));
							logger("subCharge cf_ofsCode is blank, adding statute sectionNumber(${cSubChg.statute.sectionNumber}), sectionName(${cSubChg.statute.sectionName}) and sectionCode(${cSubChg.statute.sectionCode}) to ESL que")
						} else
							logger("Adding subCharge cf_ofsCode(${cSubChg.statute.cf_ofsCode})");
					} else
						logger("No defendant subCharge to add");

					OFS_CaseXml.append("<cext:PhaseTypeText>${xmlStrUtil(sPhaseTypeText)}</cext:PhaseTypeText>");
					OFS_CaseXml.append('</cext:Charge>');
				}
            } else{
				logger("No valid party[DEF].charges found in case.party");
            }
        } else{
              OFS_CaseXml.append("<criminal:CaseCharge>");
              OFS_CaseXml.append("<criminal:ChargeOffense/>");
              OFS_CaseXml.append("<criminal:ChargeAmendedIndicator>false</criminal:ChargeAmendedIndicator>");
              OFS_CaseXml.append("</criminal:CaseCharge>");
            }
          
          
          
			OFS_CaseXml.append('</criminal:CriminalCase>');
			OFS_CaseXml.append('<FilingConfidentialityIndicator>false</FilingConfidentialityIndicator>');

			// Get lead submit filing document, the OFSSUB could be set on the party.subCase or maybe the case, so search both locations
			Document cSubDoc = null;
			String sDocExactFilename = null;
			int iDocumentSequenceID = 0;

			// Check OFSSUB status, if valid load associated leading document
            logger("Searching case for lead ${mDocStatus_.ofsSubmit} document");
            //DocumentStatus cCasDocSt = (DocumentStatus) cParty_.subCase.collect("documents[(docDef.number3!=null and docDef.number3.length()!=0) and (docDef.number4!=null and docDef.number4.length()!=0)].statuses[statusType==#p1]",(String)mDocStatus_.ofsSubmit).last();
          	DocumentStatus cCasDocSt = cRule_?._docStatus;
			if (cCasDocSt != null) {    // valid list + document?
				cSubDoc = cCasDocSt.document;    // get document if valid
				eProsCfg_.sFilingDocId_ = cCasDocSt.document.id;
			} else {
				this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("No party submitting ${mDocStatus_.ofsSubmit} document found or invalid docDef.number3 or 4 field, reviewFiling aborted")));
				return bRetVal;
			}

            // Check for valid file attached to document
            File fsDoc = cSubDoc.getFile()  // get document file
            if ( !(fsDoc != null && fsDoc.size() > 0)) { // invalid?
                this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("Submitting docId($cSubDoc.id) has no valid file or filesize == 0, review filing aborted")));
                return bRetVal;
            }

			logger("${mDocStatus_.ofsSubmit} document found - docName(${xmlStrUtil(cSubDoc?.docDef?.name)}), docNbr(${xmlStrUtil(cSubDoc?.docDef?.number)})");

			OFS_CaseXml.append("<tyler:FilingLeadDocument s:id=\"${xmlStrUtil(cSubDoc?.docDef?.number, "Filing1")?.replaceAll(" ", "")}\">");
			OFS_CaseXml.append("<nc:DocumentDescriptionText>${xmlStrUtil(cSubDoc?.docDef?.name)}</nc:DocumentDescriptionText>");

			OFS_CaseXml.append("<nc:DocumentFileControlID>${cSubDoc.id}</nc:DocumentFileControlID>"); // unique document tracking id
			OFS_CaseXml.append("<nc:DocumentSequenceID>${iDocumentSequenceID++}</nc:DocumentSequenceID>"); // unique sequence id
			OFS_CaseXml.append('<ecf:DocumentMetadata>');
          
          LookupAttribute lookupAttribute = DomainObject.find(LookupAttribute.class, "lookupItem.lookupList.name", "PARTY_SUBMIT_TYPE", "attributeType", "IOFS", "lookupItem.code", cParty_?.cf_partySubmit).find({it -> it != null});
                             
          String xmlFilingCode = com.sustain.rule.model.RuleDef.exec("INTERFACE_OFS_UPDATE_FILING_CODES_LOCAL_XML", null, ["lookuplist": "PARTY_SUBMIT_TYPE", "casecategory": "8", "name": lookupAttribute?.name, "filingcodeid": ""] ).getValue("code");

String leadDocRegisterAction = com.sustain.rule.model.RuleDef.exec("INTERFACE_OFS_UPDATE_FILING_CODES_LOCAL_XML", null, ["lookuplist": "PARTY_SUBMIT_TYPE", "casecategory": "8", "name": cSubDoc.docDef.number4, "filingcodeid": ""] ).getValue("code");
          logger("986:xmlFilingCode:${xmlFilingCode};leadDocRegisterAction:${leadDocRegisterAction}");
          String primaryFilingCode = "";
          cOldCaseFiling = cCase.collect("otherCaseNumbers[type=='CRT' && memo != null && memo.contains(#p1) && memo.contains(#p2)]", "${cParty_.firstName}".toString(),  "${cParty_.lastName}".toString()).orderBy("lastUpdated").last();
          logger("cOldCaseFiling:${cOldCaseFiling}");
          if (cOldCaseFiling == null || leadDocRegisterAction == null || leadDocRegisterAction?.isEmpty()){
            OFS_CaseXml.append("<j:RegisterActionDescriptionText>${xmlFilingCode}</j:RegisterActionDescriptionText>");
            primaryFilingCode = xmlFilingCode;
          } else {
            OFS_CaseXml.append("<j:RegisterActionDescriptionText>${leadDocRegisterAction}</j:RegisterActionDescriptionText>");
            primaryFilingCode = leadDocRegisterAction;
          }
                    
			// Atty filing
			OFS_CaseXml.append('<ecf:FilingAttorneyID>');
			//OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cRule_._ofsUniqueAttorneyId)}</nc:IdentificationID>");
            //OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cRule_._ofsUniqueAttorneyIdProduction)}</nc:IdentificationID>");
            OFS_CaseXml.append("<nc:IdentificationID></nc:IdentificationID>");
            // use above for atty unique ID
			OFS_CaseXml.append('<nc:IdentificationCategoryText>IDENTIFICATION</nc:IdentificationCategoryText>');
			OFS_CaseXml.append('</ecf:FilingAttorneyID>');

			// Party filing
			OFS_CaseXml.append('<ecf:FilingPartyID>');
			OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cParty_.partyType, "Party1")}</nc:IdentificationID>");
			OFS_CaseXml.append('<nc:IdentificationCategoryText>REFERENCE</nc:IdentificationCategoryText>');
			OFS_CaseXml.append('</ecf:FilingPartyID>');
			OFS_CaseXml.append('</ecf:DocumentMetadata>');

			// Add submitting file attachment if valid file attached, else reject filing if no file found since it's a must have
          	logger("OFS_LOG method call: getDocFileAttachment(cSubDoc, sPrimaryDocCode) ${cSubDoc} ${cSubDoc.title} ${Timestamp.valueOf(LocalDateTime.now())}");
            StringBuilder sDocFileAttachment= getDocFileAttachment(cSubDoc, sPrimaryDocCode, primaryFilingCode);
            //logger("OFS_LOG method call return: getDocFileAttachment(cSubDoc, sPrimaryDocCode) ${cSubDoc} ${cSubDoc.title} ${Timestamp.valueOf(LocalDateTime.now())}");
            if( sDocFileAttachment == null )    // test for reject error (Validation error will be set if null)
                return bRetVal;

            // Add Binary file attachment elements
            OFS_CaseXml.append(sDocFileAttachment);
          /*for( Document cDoc in cSubDoc.collect("relatedOdysseyDocuments") ) {
			OFS_CaseXml.append(getDocFileAttachment(cDoc, sPrimaryDocCode));
          }*/
			// Get/Post filing comments
            StringJoiner sStatusMemo = new StringJoiner(", ");
			if( cCase.collect("subCases.parties[partyType=='DEF']")?.size() > 1 ) // more than one DEF party involved
				sStatusMemo.add("co-defendant");
			if( cParty_.cf_partySubmit )
				sStatusMemo.add((String)LookupList.get('PARTY_SUBMIT_TYPE').findByCode((String)cParty_.cf_partySubmit).label);
          	sStatusMemo = !cParty_.case.collect("otherCaseNumbers[type == 'NYF' && endDate == null && number != null]").orderBy("id").isEmpty() ? sStatusMemo.add(" " + cParty_.case.collect("otherCaseNumbers[type == 'NYF' && endDate == null && number != null]").orderBy("id").number.toString()) : sStatusMemo;
			OFS_CaseXml.append("<tyler:FilingCommentsText>${xmlStrUtil(sStatusMemo?.toString())}</tyler:FilingCommentsText>");
			OFS_CaseXml.append('</tyler:FilingLeadDocument>');

			/** Add all other connected documents if available. Report all documents that don't have a primary filing status, but
			 * contain a specific document name
			 */
			int iConnectedFilingID = 2; // unique connecting filing ID's
			//for( Document cDoc in getAllConnectedDocFilings(cCase, cSubDoc) ) {
            logger("OFS_LOG for loop start: for( Document cDoc in cSubDoc.collect('relatedOdysseyDocuments') ) ${Timestamp.valueOf(LocalDateTime.now())}")
			for( Document cDoc in cSubDoc.collect("relatedOdysseyDocuments") ) {
                // Check for valid file attached to document
                File fcDoc = cDoc.getFile()  // get document file
                if ( !(fcDoc != null && fcDoc.size() > 0)) { // invalid?
                    this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("Connnecting docId($cDoc.id) has no valid file or filesize == 0, skipping file")));
                    return bRetVal;
                }

				logger("Attaching connected document - docName(${xmlStrUtil(cDoc.docDef.name)}), docNbr(${xmlStrUtil(cDoc.docDef.number)})");
				OFS_CaseXml.append("<tyler:FilingConnectedDocument s:id=\"${xmlStrUtil(cDoc?.docDef?.number, "ConnectedFiling${iConnectedFilingID++}")?.replaceAll(" ", "")}\">");
                //OFS_CaseXml.append("<tyler:FilingLeadDocument s:id=\"${xmlStrUtil(cDoc?.docDef?.number, "Filing${iConnectedFilingID++}")?.replaceAll(" ", "")}\">");
				OFS_CaseXml.append("<nc:DocumentDescriptionText>${xmlStrUtil(cDoc?.docDef?.name)}</nc:DocumentDescriptionText>");
				OFS_CaseXml.append("<nc:DocumentFileControlID>${cDoc.id}</nc:DocumentFileControlID>");
				OFS_CaseXml.append("<nc:DocumentSequenceID>${iDocumentSequenceID++}</nc:DocumentSequenceID>");
				OFS_CaseXml.append('<ecf:DocumentMetadata>');
String connectingDocRegisterAction = com.sustain.rule.model.RuleDef.exec("INTERFACE_OFS_UPDATE_FILING_CODES_LOCAL_XML", null, ["lookuplist": "PARTY_SUBMIT_TYPE", "casecategory": "8", "name": cDoc.docDef.number4, "filingcodeid": ""] ).getValue("code");
              logger("1047:cDoc.docDef.number3:${cDoc.docDef.number3};connectingDocRegisterAction:${connectingDocRegisterAction}");
				OFS_CaseXml.append("<j:RegisterActionDescriptionText>${connectingDocRegisterAction}</j:RegisterActionDescriptionText>");
				// Atty filing
				OFS_CaseXml.append('<ecf:FilingAttorneyID>');
				OFS_CaseXml.append("<nc:IdentificationID></nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>IDENTIFICATION</nc:IdentificationCategoryText>');
				OFS_CaseXml.append('</ecf:FilingAttorneyID>');

				// Party filing
				OFS_CaseXml.append('<ecf:FilingPartyID>');
				OFS_CaseXml.append("<nc:IdentificationID>${xmlStrUtil(cParty_.partyType, "Party1")}</nc:IdentificationID>");
				OFS_CaseXml.append('<nc:IdentificationCategoryText>REFERENCE</nc:IdentificationCategoryText>');
				OFS_CaseXml.append('</ecf:FilingPartyID>');
				OFS_CaseXml.append('</ecf:DocumentMetadata>');

				// Add connecting file attachment
                logger("OFS_LOG connecting file: method call: getDocFileAttachment(cSubDoc, sPrimaryDocCode) ${cDoc} ${cDoc.title} ${Timestamp.valueOf(LocalDateTime.now())}");
              if( (sDocFileAttachment=getDocFileAttachment(cDoc, sConnectingDocCode, connectingDocRegisterAction)) == null ){
                    return bRetVal;
              }
				//logger("OFS_LOG connecting file: method call return: getDocFileAttachment(cSubDoc, sPrimaryDocCode) ${cDoc} ${cDoc.title} ${Timestamp.valueOf(LocalDateTime.now())}");
                OFS_CaseXml.append(sDocFileAttachment); // add file metadata section

				OFS_CaseXml.append('</tyler:FilingConnectedDocument>');
                //OFS_CaseXml.append('</tyler:FilingLeadDocument>');
			}
			logger("OFS_LOG for loop end: for( Document cDoc in cSubDoc.collect('relatedOdysseyDocuments') ) ${Timestamp.valueOf(LocalDateTime.now())}")
			OFS_CaseXml.append( '</CoreFilingMessage>' );

			// Filing payment message
			logger("Attaching payment message")
			OFS_CaseXml.append( '<PaymentMessage xsi:schemaLocation="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:PaymentMessage-4.0 ..\\..\\..\\Schema\\message\\ECF-4.0-PaymentMessage.xsd" '
								+ 'xmlns="urn:oasis:names:tc:legalxml-courtfiling:schema:xsd:PaymentMessage-4.0" '
								+ 'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
								+ 'xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" '
								+ 'xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">');
			OFS_CaseXml.append( '<FeeExceptionReasonCode />')
			OFS_CaseXml.append( '<FeeExceptionSupportingText />');
			OFS_CaseXml.append( '<PayerName />');
			OFS_CaseXml.append( '<cac:AllowanceCharge>');
			OFS_CaseXml.append( '<cbc:ChargeIndicator>true</cbc:ChargeIndicator>');
    		OFS_CaseXml.append( '<cbc:Amount currencyID="USD">0.00</cbc:Amount>');
			OFS_CaseXml.append( '<cac:TaxCategory>');
			OFS_CaseXml.append( '<cbc:Percent>.00</cbc:Percent>');
        	OFS_CaseXml.append( '<cac:TaxScheme />');
			OFS_CaseXml.append( '</cac:TaxCategory>');
    		OFS_CaseXml.append( '<cac:TaxTotal>');
			OFS_CaseXml.append( '<cbc:TaxAmount currencyID="USD">.00</cbc:TaxAmount>');
			OFS_CaseXml.append( '</cac:TaxTotal>');
    		OFS_CaseXml.append( '<cac:PaymentMeans>');
        	OFS_CaseXml.append( '<cbc:PaymentMeansCode />');
            OFS_CaseXml.append( '<cbc:PaymentID></cbc:PaymentID>');
            // production payment id 26ebdb4e-f36d-473e-a5ab-c2f8eb0e9cd3
			//OFS_CaseXml.append( '<cbc:PaymentID>26ebdb4e-f36d-473e-a5ab-c2f8eb0e9cd3</cbc:PaymentID>');
            // staging payment id 95e28d07-d933-4963-88e4-2772d6f8448a
            //OFS_CaseXml.append( '<cbc:PaymentID>95e28d07-d933-4963-88e4-2772d6f8448a</cbc:PaymentID>');
    		OFS_CaseXml.append( '</cac:PaymentMeans>');
			OFS_CaseXml.append( '</cac:AllowanceCharge>');
			OFS_CaseXml.append( '<cac:Address>');
			OFS_CaseXml.append( '</cac:Address>');
			OFS_CaseXml.append( '<cac:Payment>');
			OFS_CaseXml.append( '</cac:Payment>');
			OFS_CaseXml.append( '</PaymentMessage>');

			// Add ePros configuration to the reviewFiling message
			logger("Attaching eProsCfg tag");
          //String caseCourtNumber = !cCase.collect("otherCaseNumbers[type=='CRT']").isEmpty() ? cCase.collect("otherCaseNumbers[type=='CRT']").orderBy("lastUpdated").last().number : "";
          CaseAssignment filingAttorney = cCase.collect("assignments[assignmentRole == 'ATTY' && status == 'CUR']").find({it -> it.person != null && it.person.lastName != null});
          filingAttorney = filingAttorney == null ? cCase.collect("assignments[assignmentRole == 'REV' && status == 'CUR']").find({it -> it.person != null && it.person.lastName != null}) : filingAttorney;
          filingAttorney = filingAttorney != null && filingAttorney.person != null && filingAttorney.person.lastName != null && !filingAttorney.person.collect("identifications[identificationType == 'BAR' && identificationNumber != null]").isEmpty() ? filingAttorney : null;
          String attyBarNumber = filingAttorney != null ? filingAttorney.person.collect("identifications[identificationType == 'BAR']").first()?.identificationNumber : "236992";
          String attyLastName = filingAttorney != null ? filingAttorney.person.lastName : "Muia";
          String attyFirstName = filingAttorney != null ? filingAttorney.person.firstName : "Anthony";
          String attyMiddleName = filingAttorney != null ? filingAttorney.person.middleName : "";
          logger("1141:cParty_:${cParty_}");
          String otherCaseNumberMemo = cCase.collect("otherCaseNumbers[type == 'CRT' && memo != null && memo.contains(#p1)]", "${cParty_.fml}".toString()).find({thisOCN -> thisOCN != null})?.memo;
          otherCaseNumberMemo = otherCaseNumberMemo == null ? "" : otherCaseNumberMemo;
            OFS_CaseXml.append( '<eProsCfg>');
          if (otherCaseNumberMemo != null && !otherCaseNumberMemo.isEmpty()){
            OFS_CaseXml.append( "<CaseInitialFilingID>${otherCaseNumberMemo.substring(0, otherCaseNumberMemo.indexOf(" ")).trim()}</CaseInitialFilingID>" );
            OFS_CaseXml.append( "<CaseTitle>${otherCaseNumberMemo.substring(otherCaseNumberMemo.indexOf(" ")).trim()}</CaseTitle>" );
          } else {
            OFS_CaseXml.append( "<CaseTitle></CaseTitle>" );
            OFS_CaseXml.append( "<CaseInitialFilingID></CaseInitialFilingID>" );
          }
            OFS_CaseXml.append( "<CaseCourtLocation>${xmlStrUtil(eProsCfg_.sCaseCourtLocation_)}</CaseCourtLocation>" );
          if (eProsCfg_.sCaseNumber_ == null){
            OFS_CaseXml.append( "<CaseNumber/>" );
            OFS_CaseXml.append( "<CaseDocketNumber/>" );
          } else {
            OFS_CaseXml.append( "<CaseNumber>${eProsCfg_.sCaseNumber_}</CaseNumber>" );
            OFS_CaseXml.append( "<CaseDocketNumber>${eProsCfg_.sCaseNumber_}</CaseDocketNumber>" );
          }
            OFS_CaseXml.append( "<AttyBarNumber>${attyBarNumber}</AttyBarNumber>" );
            OFS_CaseXml.append( "<AttyLastName>${attyLastName}</AttyLastName>" );
            OFS_CaseXml.append( "<AttyFirstName>${attyFirstName}</AttyFirstName>" );
            OFS_CaseXml.append( "<AttyMiddleName>${attyMiddleName}</AttyMiddleName>" );
			OFS_CaseXml.append( "<FilingDocId>${xmlStrUtil(eProsCfg_.sFilingDocId_)}</FilingDocId>" );

			// Add all missing statutes
			OFS_CaseXml.append( "<MissingStatutes>");
			for( ChargeStatuteObj ms in aMissingOfsStatuteList) { // cue sectionNbr for ELS
   				OFS_CaseXml.append( "<ChargeSequenceID id=\"${xmlStrUtil(ms.sChargeID_)}\">" );
				OFS_CaseXml.append( "<StatuteCodeIdentificationWord>${xmlStrUtil(ms.sStatuteSectionNumber_)}</StatuteCodeIdentificationWord>");
	 			OFS_CaseXml.append( "<StatuteCodeIdentificationDesc>${xmlStrUtil(ms.sStatuteSectionName_)}</StatuteCodeIdentificationDesc>");

                // Add charge statute attributes
                OFS_CaseXml.append( "<StatuteCodeAttributes>" );
                if( ms.lsStatuteAttributeLst_ != null ) { // attributes?
                    for( String at in ms.lsStatuteAttributeLst_ )
 						OFS_CaseXml.append( "<StatuteCodeAttributeWord>${xmlStrUtil(at)}</StatuteCodeAttributeWord>");
                } else { // default
                    OFS_CaseXml.append( "<StatuteCodeAttributeWord />" );
                }
                OFS_CaseXml.append( "</StatuteCodeAttributes>" );

		     	OFS_CaseXml.append( "<AdditionalStatutes>" );
				OFS_CaseXml.append( "<AdditionalStatute>" );
				OFS_CaseXml.append( "<StatuteCodeIdentificationWord />" );
				OFS_CaseXml.append( "<StatuteCodeIdentificationDesc />" );
                OFS_CaseXml.append( "<StatuteCodeAttributes>" );
                OFS_CaseXml.append( "<StatuteCodeAttributeWord/>" );
                OFS_CaseXml.append( "</StatuteCodeAttributes>" );
				OFS_CaseXml.append( "</AdditionalStatute>" );
				OFS_CaseXml.append( "</AdditionalStatutes>" );
				OFS_CaseXml.append( "</ChargeSequenceID>" );
			}
			OFS_CaseXml.append( "</MissingStatutes>" );

			OFS_CaseXml.append( '</eProsCfg>')
			OFS_CaseXml.append( '</ReviewFilingRequestMessage>' );

			// Write/queue reviewFiling xml payload to disk
			String sSmbReviewFilePath= null;
			try {

				// Connect to SMB network file share object and set credentials
				SmbFileWrapper cSmbFileShareObj = new SmbFileWrapper(cRule_._eslOutboundFilingSmbPath, cSysProps_.sSmbFileUsername_,cSysProps_.sSmbFilePassword_,"");
				if( !cSmbFileShareObj.isConnected() ) {
					this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("Error connecting to ${cRule_._eslOutboundFilingSmbPath} network URL to write reviewFiling, check server/user/pwd credentials")));
					return bRetVal;
				}

				// Create path + filename (e.g. Case#_ddMMyyyyHHmmssSSS.xml)
				String sTimeStamp = new SimpleDateFormat("ddMMyyyyHHmmssSSS").format(new Date());
				String uniqueFileName = "${cCase.caseNumber}_${sTimeStamp}.xml";
                sSmbReviewFilePath= cSmbFileShareObj.smbURL + uniqueFileName;

				logger "Writing OFS reviewFiling xml payload to $sSmbReviewFilePath";

				// Validate xml and write xml to smb network file share
              	logger("OFS_LOG saving file to queued directory")
				String sXmlSchema = XmlUtil.serialize(OFS_CaseXml.toString());	// validate/condense xml nodes
				/*if( !cSmbFileShareObj.putFile(sXmlSchema, sSmbReviewFilePath) ) { // write file, error?
					this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("Error writing reviewFiling ${sSmbReviewFilePath} to network share")));
					return bRetVal;
				}*/
              /*OFS_File_Update*/
              File courtFiling = new File("${cRule_._eslOutboundFilingSmbPath}\\${uniqueFileName}".toString());
              BufferedWriter bw = new BufferedWriter(new FileWriter(courtFiling));
              bw.write(sXmlSchema, 0, sXmlSchema.length());
              bw.close();
              logger("OFS_LOG updating file ${courtFiling} ; is file ${courtFiling.isFile()}");
              XmlSlurper parser = new XmlSlurper();
			  FileReader fr = new FileReader(courtFiling);
			  BufferedReader bf = new BufferedReader(fr);
			  String xmlFileContents = "";
			  Stream<String> line = bf.lines();
              logger("OFS_LOG line ${line}")
			  ArrayList array = (ArrayList)line.toArray();
			  for (i in array){
				xmlFileContents += i;
			  }
			  fr.close();
			  bf.close();
			  GPathResult xmlIPSluper = parser.parseText(xmlFileContents);
              xmlIPSluper.CoreFilingMessage.FilingLeadDocument.DocumentRendition.DocumentRenditionMetadata.DocumentAttachment.BinaryBase64Object.replaceBody(org.apache.commons.codec.binary.Base64.encodeBase64String(cRule_._docStatus.document.file.bytes));
              for (relatedOdysseyDocument in cRule_._docStatus.document.collect("relatedOdysseyDocuments")){
                GPathResult filingConnectedDocument = xmlIPSluper.CoreFilingMessage.FilingConnectedDocument.find{ it -> it.DocumentFileControlID.text() == relatedOdysseyDocument.id.toString()};
				filingConnectedDocument.DocumentRendition.DocumentRenditionMetadata.DocumentAttachment.BinaryBase64Object.replaceBody(org.apache.commons.codec.binary.Base64.encodeBase64String(relatedOdysseyDocument.file.bytes));
              }
              PrintWriter writer = new PrintWriter(courtFiling);
			  writer.print(XmlUtil.serialize(xmlIPSluper));
			  writer.flush();
			  writer.close();
              org.apache.commons.io.FileUtils.moveFileToDirectory(courtFiling, new File(cRule_._ofsOutboundQueuedDirectory), false);
              //org.apache.commons.io.FileUtils.moveFileToDirectory(courtFiling, new File("\\\\torreypines\\OFS\\out\\queued"), false);
              /*OFS_File_Update*/
				// Set filing status attributes
				this.bReviewFilingSent_= true; // indicate filing sent to queue
				bRetVal= true;	// indicate success

			} catch (Exception ex) {
				this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, logger("$sSmbReviewFilePath file write / xml validation error, reviewFiling aborted")));
				logger ex.message
			}
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::CreateCriminalDefCourtFilingMessage - Def Party reviewFiling error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return bRetVal;
	}

	/** -------------------------------------------------------------------------------------------------------
	 * Method used to test input buffer for valid string. If string is valid return it. Otherwise if string
	 * is null add default if present. If no default, return an empty string reference.
	 * @param sBuf Class:String input buffer
	 */
	public String xmlStrUtil( String sBuf, String sDefault="" ) {
		if( sBuf == null ) { // null?
			return( (!StringUtil.isNullOrEmpty(sDefault))? sDefault: "" );
		}else{ // valid!
			if( sBuf == 'null' ) sBuf=""; // clear out null strings
			return( sBuf.trim() );
		}
	}

	/** --------------------------------------------------------------------------------------------------------------------
	 * Initialize document status collection. When a document is submitted to court, it will be set to OFSSUB status. Since
	 * the status is tracked through-out the process, we only need one status set to OFSSUB at a time. In most cases there
	 * will only be one, but just in case, this method will search for the newest status w/ OFSSUB and will remove any extra
	 * OFSSUB statuses if they exist.
 	 * @param cParty
	 * @returns true if successful or false if no 'OFSSUB' found
	 */
	public boolean initializeDocumentStatus( Party cParty ) {
		boolean bRetVal = false;

		try {
			logger "Searching for valid DEF 'OFSSUB' document status set";
			DocumentStatus cDocStatus = cRule_._docStatus;
			if( cDocStatus != null ) {
				logger "Lastest document w/ ${mDocStatus_.ofsSubmit} status found - ${cDocStatus.dateCreated}";

				// Cleanup any older 'OFS???' statuses leaving just the newest status if exists
				/*cParty.subCase.collect("documents.statuses[!(statusType==#p1 and dateCreated==#p2)]",(String)mDocStatus_.ofsSubmit,cDocStatus.dateCreated).findAll{
					s -> mDocStatus_.find { it.value == s.statusType }?.key != null }.each{ ds ->
						logger "Removing older $ds.statusType document status - $ds.dateCreated";
						//ds.deleteRemoveFromPeers(); // remove old status
				}*/
				bRetVal= true;	// report success
			}

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::InitializeDocumentStatus - Error initializing document statuses");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
		return bRetVal;
	}


	/** ----------------------------------------------------------------------------------------------------
	 *  Method to retrieve lookup attribute code based on a lookupList code and attribute type.
	 *  @param sLookupList - LookupList name
	 *  @param sCode - LookupList code of attribute.
	 *  @param sAtrType - Attribute type of attribute to extract.
	 *  @param bUseDefaultCode - If true, return code value as default if no attribute found.
	 *  @returns attribute of code if found. Otherwise returns default code.
	 */
	/*public String getLookupListCodeAttribute( String sLookupList, String sCode, String sAtrType, boolean bUseDefaultCode=true ) {
		String sAtr= (bUseDefaultCode)? sCode: null;
		if( sCode != null ) { // valid code?
			logger "Searching lookupList[$sLookupList] code[$sCode] for related attributeType[$sAtrType]";
			LookupAttribute cAttr = DomainObject.find(LookupAttribute.class, "lookupItem.lookupList.name", sLookupList, "attributeType", "IOFS", "lookupItem.code", sCode).find({it -> it != null});
			//cAttr = cAttr == null ? (LookupAttribute) LookupAttribute.getAttribute(sLookupList,  sCode, sAtrType) : cAttr;
			if (cAttr != null) {
				sAtr = cAttr.value;
				logger("Attribute= $sAtr found");
			}
		}
		return sAtr;
	}*/
  
public String getLookupListCodeAttribute( String sLookupList, String sCode, String sAtrType, String courtCode, String caseCategory,  String filingCodeID, String countryCode) {
String sAtr= sCode;
String attrValue = "";
if( sCode != null ) {
  LookupAttribute cAttr = DomainObject.find(LookupAttribute.class, "lookupItem.lookupList.name", sLookupList, "attributeType", "IOFS", "lookupItem.code", sCode).find({it -> it != null});
  cAttr = cAttr == null ? DomainObject.find(LookupAttribute.class, "lookupItem.lookupList.name", sLookupList, "attributeType", "IOFS", "lookupItem.label", sCode).find({it -> it != null}) : cAttr;
  attrValue = com.sustain.rule.model.RuleDef.exec("INTERFACE_OFS_UPDATE_FILING_CODES_LOCAL_XML", null, ["lookuplist": sLookupList, "casecategory": caseCategory, "name": cAttr?.name, "filingcodeid": filingCodeID] ).getValue("code");
  
  if (!attrValue.isEmpty()) {
    sAtr = attrValue;
  } else {
    sAtr = cAttr?.value;
  }
}
  return sAtr;
}

  
	/** ----------------------------------------------------------------------------------------------
	 * Update document status w/ input parameters
	 * @param cParty
	 * @param sWith - Find with
	 * @param sTo - Set to
	 * @Returns - Nothing
	 */
	public void updateDocumentStatus( Party cParty, String sWith, String sTo ){
		if( cParty == null )
			return;

		try {
			logger "Searching for latest document status $sWith";
			DocumentStatus cDocStatus = cRule_._docStatus;
			if( cDocStatus != null ) {
				logger "Found status w/ $sWith set on doc, updating latest to $sTo";
				cDocStatus.statusType = sTo;
				cDocStatus.beginDate = new Date();
				cDocStatus.saveOrUpdate();
			} else
				logger "No document statuses found w/ $sWith";

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::updateDocumentStatus - Error updating latest document status w/ $sWith to $sTo");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
	}

	/** --------------------------------------------------------------------------------------------------------------
	 *  This method is called when the cf_ofsCode is blank. It will generate a custom OFS sectionNumber using
	 *  the chargeAttributes field. If there is a chargeAttribute marked, the attribute of type OIFS will be queried
	 *  on the lookup list and the Value will be added to the statute list and then to the eProsCfg xml object.
	 *  @param cCharge - Class:Charge - Party charge
	 *  @Returns list of statute attributes
	 */
	public List<String> getOfsChgStatuteAttributes( Charge cCharge )
	{
		if( cCharge.statute == null )   // no statute?
            return null;

        // Create attribute holding list
        List<String> lAttributes= new ArrayList<String>();

		// Add prefixes to statuteNumber for all chargeAttributes sorted in ascending order (e.g. 664-182-SectionNbr)
		logger "Searching for charge statute attributes";
		cCharge.chargeAttributes.sort().each{ ca ->	   // sort/load in ascending order
			String sAttr= getLookupListCodeAttribute("CHARGE_ATTRIBUTES", ca, OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US");
			if( sAttr != null ) { // valid attr?
				lAttributes.add(sAttr);
			}
		}
		return lAttributes;
	}

	/** -------------------------------------------------------------------------------------------------------
	 * Method to create and return a file attachment xml structure. If no document found or an invalid doc file
	 * is found a blank default attachment node structure will be returned.
	 *
	 * @param cDoc - Document contain file attachment
	 * @param sDocTypeId - Document Id type (332= Connecting Doc/Submitting doc)
	 * @param cDoc - Document containing file attachment
	 * @returns xml file attachment elements if successful, else returns null if error.
	 */
	public StringBuilder getDocFileAttachment( Document cDoc, String sDocTypeId, String filingCodeRegisterAction ) {
		StringBuilder xFile = new StringBuilder();
		String sDocExactFilename = null;
		String sBase64EncodedDocFile = null;

		Set<String> sAllowableFilingExts = ['PDF'];

		try{
			// Load file attachment structure if valid file
			if( cDoc != null ) {
				// Encode document file if available
				//sBase64EncodedDocFile = base64EncodeDocFile(cDoc);
              	//sBase64EncodedDocFile = cDoc.file.bytes.encodeBase64().toString();
                //sBase64EncodedDocFile = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(cDoc.file.bytes);
                logger("OFS_LOG encoding file start ${cDoc} ${Timestamp.valueOf(LocalDateTime.now())}");
              	sBase64EncodedDocFile = org.apache.commons.codec.binary.Base64.encodeBase64String(cDoc.file.bytes);  //this method was much faster
                logger("OFS_LOG encoding file end ${cDoc} ${Timestamp.valueOf(LocalDateTime.now())}");
              	//sBase64EncodedDocFile = org.apache.commons.codec.binary.Base64.encodeBase64(cDoc.file.bytes).toString();
                //sBase64EncodedDocFile = org.codehaus.groovy.runtime.EncodingGroovyMethods.encodeBase64(cDoc.file.bytes).toString();
              	//sBase64EncodedDocFile = java.util.Base64.getEncoder().encodeToString(cDoc.file.bytes);
				if (sBase64EncodedDocFile != null ) { // valid
					sDocExactFilename = cDoc.getFile().name;    // get file+ext

					// Test for valid file extension for attachment
					if( !StringUtil.isNullOrEmpty(sDocExactFilename) ) { // valid file?
						String sFileExt = FilenameUtils.getExtension(sDocExactFilename);
						if ( !sAllowableFilingExts.contains(sFileExt?.toUpperCase()) ) {
							this.aErrorList_.add(new ValidationError(true, cDoc.case.caseNumber, logger("Invalid [$sDocExactFilename] file, filing system only supports ${sAllowableFilingExts} types")));
							return null;
						}
					}

					// The RegisterActionDescriptionText (ie. docDef.number3) field is a required field
					if ( StringUtil.isNullOrEmpty(cDoc?.docDef?.number3) ) {
						this.aErrorList_.add(new ValidationError(true, cDoc.case.caseNumber, logger("docDef.number3 required field is null on attached document")));
						return null;
					}
				} else {    // encoding error (Validation error set)
					this.aErrorList_.add(new ValidationError(true, cDoc.case.caseNumber, logger("Base64 encoding error on document file [$sDocExactFilename]")));
					return null;
				}
			}

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getDocFileAttachment - file encoding error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return null;
		}
		logger("OFS_LOG adding file to xml node start ${cDoc} ${Timestamp.valueOf(LocalDateTime.now())}");
      String tylerCourtLocation = "fresno:cr";
      String tylerCaseCategory = "8";
		// Build xml file attachment elements
        xFile.append('<ecf:DocumentRendition>');
        xFile.append('<ecf:DocumentRenditionMetadata>');
		xFile.append('<ecf:DocumentAttachment>');
		//xFile.append("<nc:BinaryBase64Object>${xmlStrUtil(sBase64EncodedDocFile)}</nc:BinaryBase64Object>");
        xFile.append("<nc:BinaryBase64Object></nc:BinaryBase64Object>");
		xFile.append("<nc:BinaryDescriptionText>${xmlStrUtil(sDocExactFilename)}</nc:BinaryDescriptionText>");
      String codeBinaryFormatStandardName = com.sustain.rule.model.RuleDef.exec("INTERFACE_OFS_UPDATE_FILING_CODES_LOCAL_XML", null, ["lookuplist": "ODYSSEY_BINARY_FORMAT_STANDARD", "casecategory": "8", "name": "", "filingcodeid": filingCodeRegisterAction] ).getValue("code");
      codeBinaryFormatStandardName = codeBinaryFormatStandardName == null || codeBinaryFormatStandardName.isEmpty() ? xmlStrUtil(getLookupListCodeAttribute("ODYSSEY_BINARY_FORMAT_STANDARD", "CRIMINALDOCUMENT", OFS_ATTRIBUTE_TYPE_, tylerCourtLocation, tylerCaseCategory, "filingCode", "US")) : codeBinaryFormatStandardName;
        xFile.append("<nc:BinaryFormatStandardName>${codeBinaryFormatStandardName}</nc:BinaryFormatStandardName>");
		xFile.append("<nc:BinaryLocationURI>${xmlStrUtil(sDocExactFilename)}</nc:BinaryLocationURI>");
		xFile.append("<nc:BinaryCategoryText>${xmlStrUtil(sDocTypeId)}</nc:BinaryCategoryText>");
		xFile.append('</ecf:DocumentAttachment>');
        xFile.append('</ecf:DocumentRenditionMetadata>');
        xFile.append('</ecf:DocumentRendition>');
        logger("OFS_LOG adding file to xml node end ${cDoc} ${Timestamp.valueOf(LocalDateTime.now())}");
        logger("OFS_LOG method call complete: getDocFileAttachment(cSubDoc, sPrimaryDocCode) ${cDoc} ${cDoc.title} ${Timestamp.valueOf(LocalDateTime.now())}");
		return xFile;
	}

	/** --------------------------------------------------------------------------------------------------------
	 * Get all additional connecting documents attached to initial filing using on case cross reference. Where
	 * the caseCrossReference lid = lead doc, rid = ref connect doc or vise-versa.
	 *
	 * @param cCase - Submitting case
     * @param cSubDoc - Submit filing doc
	 * @Returns list of documents matching criteria, else null if error
	 */
	public List<Document> getAllConnectedDocFilings( Case cCase, Document cSubDoc ) {
		if ( cCase == null || cSubDoc == null )
			return null;

		try {
			List<Document> lCDocs = [];
			logger("Searching case.caseCrossReferences for connecting Docs to attach w/ filing");

            // Search for connecting documents attached to initial filing document, search by Rid then by Lid if needed
			boolean bLeadDocOnLid= false;
			String sDocSrchFilterRid = "crossReferences[lid!=null and rid==#p1 and rentity!=null and type=='DOCREL']"
			String sDocSrchFilterLid = "crossReferences[lid==#p1  and rid!=null and rentity!=null and type=='DOCREL']"
			List<CaseCrossReference> lCaseCrossRefs=cCase.collect(sDocSrchFilterRid,cSubDoc.id);
			if( lCaseCrossRefs.empty ) { // search by lead doc on lid?
				logger("CrossRef lead doc not found by Rid, searching by Lid");
				lCaseCrossRefs=cCase.collect(sDocSrchFilterLid,cSubDoc.id);
				bLeadDocOnLid= true; // search mode
			}

			// If lead document found on Lid use Rid for connecting document id and vise-versa
			lCaseCrossRefs.each { CaseCrossReference cr ->
				Document cConnDoc = (bLeadDocOnLid)? Document.get(cr.rid): Document.get(cr.lid);
				if( cConnDoc!=null ) {
					lCDocs.add(cConnDoc);    // add doc to list
					logger("Loading connecting document - [${cConnDoc?.docDef?.number4} | ${cConnDoc?.docDef?.name}]");
				}else{
					this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("Connecting DocId(${cr.rid}) not found in caseCrossReference})")));
				}
			}

			if ( lCDocs != null ) { // valid connecting docs available?
				logger("Found ${lCDocs.size()} connecting Docs on party.subCase to attach");
				return lCDocs;
			}
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getAllConnectedDocFilings - Error searching document statuses");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
		return null;
	}

	/** -----------------------------------------------------------------------------------
	 * Get Function to encode document file bytes into a binary base64 string
	 * @param fFile - File to encode
	 * @Returns encoded base64 encoded string or null if error
	 */
	public String base64EncodeDocFile( Document cDoc ) {
		if( cDoc == null ) return null;
		try {
			// Attempt to load document file and add and return base64 string encoding
			File fDoc= cDoc.getFile()	// get document file
			if ( fDoc != null && fDoc.size() > 0 ) {	// valid file?
				logger("Applying base64 binary encoding on document $fDoc.name file");
				return( fDoc.bytes.encodeBase64().toString() );
			} else
				logger("No file attachment found");

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::base64EncodeDocFile - Document base64 encoding error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
		return null;
	}

	/** ------------------------------------------------------------------------------------
	 *  Function to convert date and time into a String object
	 * @param sDateTime - Date/Time string to convert to Date type
	 * @param sDateFormat - Date format to use (eg. "yyyyMMddHHmm")
	 * Returns - formatted String object
	 * ------------------------------------------------------------------------------------*/
	public String convDateFmtToStr(Date dDateTime, String sFormat) {
		if( dDateTime == null )    // empty?
			return null;

		// Convert dateTime string to selected date format
		try {
			logger("Converting Date($dDateTime) string to $sFormat format")
			DateFormat df = new SimpleDateFormat(sFormat);
			String sRetDate = df.format(dDateTime);
			logger(" Date($dDateTime) converted to $sRetDate");
			return sRetDate
		}
		catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::convDateFmtToStr - Date to String conversion error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return null;
		}
	}

	/** ------------------------------------------------------------------------------------
	 * Combine calendar date/time objects into a single date object. If time is missing
	 * just return the date.
	 * @param date - date format
	 * @param time - time format
	 * Returns - returns combined time/date object, else null if error
	 */
	public Date combineDateTime(Date date, Date time) {
		if( date == null )
			return null;

		// If no time is present, just return date
		if( time == null )
			return date;

		Calendar aDate = Calendar.getInstance();
		aDate.setTime(date);

		Calendar aTime = Calendar.getInstance();
		aTime.setTime(time);

		Calendar aDateTime = Calendar.getInstance();
		aDateTime.set(Calendar.DAY_OF_MONTH, aDate.get(Calendar.DAY_OF_MONTH));
		aDateTime.set(Calendar.MONTH, aDate.get(Calendar.MONTH));
		aDateTime.set(Calendar.YEAR, aDate.get(Calendar.YEAR));
		aDateTime.set(Calendar.HOUR, aTime.get(Calendar.HOUR));
		aDateTime.set(Calendar.MINUTE, aTime.get(Calendar.MINUTE));
		aDateTime.set(Calendar.SECOND, aTime.get(Calendar.SECOND));

		return aDateTime.getTime();
	}

	/** ------------------------------------------------------------------------------------
	 * Convert date to ISO 8601 date string format
	 * @param dDate - date format
	 * Returns - return ISO 8601 date string, Otherwise null
	 */
	public String toISO8601UTC(Date date) {
		if( date == null ) return null;

		TimeZone tz = TimeZone.getTimeZone("UTC");
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
		df.setTimeZone(tz);
		return df.format(date);
	}

	/** --------------------------------------------------------------------------------------------------
	 * Convert Ft'In" format to inches.
	 * @param sFtInStr Class:String Ft'In" formatted input string
	 * @return converted string, else null if error
	 */
	public String convertFtInFmtToInches( String sFtInStr ) {
		String sConv= null;
		int iFt= 0, iIn= 0;

		if( sFtInStr == null )	// invalid string?
			return null;

		// Remove eCourt unrelated characters
		sFtInStr= sFtInStr.replace("''","\""); // default to " to support '' or " inch mode
		sFtInStr= sFtInStr.replace("_",""); // get rid of eSuite delimiter

		try {
			logger("Converting $sFtInStr to inches")
			int iFtPos= sFtInStr.indexOf("'");	// get Ft position
			if( iFtPos > 0 ) { // found?
				iFt= sFtInStr.substring(0, iFtPos).toInteger();
				int iInPos= sFtInStr.indexOf("\"");   // get In position
				if( iInPos > 0 ) {  // found?
					iIn = sFtInStr.substring(iFtPos+1,iInPos).toInteger();
					sConv= ((iFt*12) + iIn).toString(); // convert to inches
					logger("Converted $sFtInStr to $sConv\"");
				} else
					logger("Invalid \" delimiter found - (FT'IN\")");
			} else
				logger("Invalid ' delimiter found - (FT'IN\")");
		} catch (Exception ex) {
			logger "Exception::convertFtInFmtToInches - Invalid FT/IN($sFtInStr) format";
			logger ex.message;
		}
		return sConv;
	}

	/** ------------------------------------------------------------------------------------
	 * Lookup list validation type enumeration class
	 */
	public class LListValType {
		public static final int code = 0x1;  // search by code and use code
		public static final int label = 0x2;  // search by label and use code
		public static final int attrib = 0x4;  // search by attrib and use code/name
		public static final int all = 0x7;  // use all search methods
	}

	/** ----------------------------------------------------------------------------------------------------------------------------------------
	 * Method to validate source value/code based on the lookup validationType enumeration.
	 * @param sSourceVal - Class:String - Source value to validate
	 * @param sLookupList - Class:String - LookupList to use for source value validation
	 * @param nValidateType - Class:LListValType - Validation type
	 * @param mMapTranslation - Class:HashMap - Internal Map for translation
	 * @param sDefault - Class String - Default value to use if search fails
	 * @returns source value if search found or default used, else returns null
	 */
	public String validateLookupCode(
			String sSourceVal, String sLookupList,
			int nValidateType = LListValType.all, List mMapTranslation = null, String sDefault = null) {
		String sRetVal = null;
		LookupList lLookupList = null;

		// validate source value
		if( StringUtil.isNullOrEmpty(sSourceVal) )
			return null;

		// Load lookup list to use for field validation
		if ( !StringUtil.isNullOrEmpty(sLookupList) )  // valid lookup?
			lLookupList = LookupList.get(sLookupList);  // load lookupList
		else
			return null;

		// Validate/Translate source value
		logger("Validating($sSourceVal) w/ LookupList[$sLookupList] using vType($nValidateType)");
		if (lLookupList) { // valid lookup?
			// Search by code, if found, keep to the code =)
			if ((nValidateType & LListValType.code)) {
				if (lLookupList.exists(sSourceVal))  // if exist, use code
					sRetVal = sSourceVal;
			}

			// Search by label, if found, use code
			if (StringUtil.isNullOrEmpty(sRetVal) && (nValidateType & LListValType.label)) {
				if (lLookupList.findByLabel(sSourceVal))  // search by label, if exist, use associated code?
					sRetVal = lLookupList.findByLabel(sSourceVal).code;
			}

			// Search by attribute, if found, use name/code associated w/ value
			if (StringUtil.isNullOrEmpty(sRetVal) && (nValidateType & LListValType.attrib)) {
				List<LookupAttribute> lAttrib = lLookupList.collect("items.attributes[value==#p1]", sSourceVal);
				if ( !lAttrib.empty )  // valid?
					sRetVal = lAttrib.last().getName();  // get name/code for value
			}

			// Search by internal map if code not found in previous search
			if ( StringUtil.isNullOrEmpty(sRetVal) && mMapTranslation) {        // prev search failed w/ valid map?
				if (mMapTranslation.get(sSourceVal)) // in custom mapping?
					sRetVal = mMapTranslation.get(sSourceVal);
			}

			// Use valid default if search failed
			if ( StringUtil.isNullOrEmpty(sRetVal) && !StringUtil.isNullOrEmpty(sDefault) )  // use default?
				sRetVal = sDefault;
		} else {
			logger(" Lookuplist[$sLookupList] invalid");
		}
		logger(" Validation results = " + ((sRetVal) ? "($sRetVal) valid" : "($sSourceVal) invalid"));

		return sRetVal;
	}

	/** ----------------------------------------------------------------------------------------------
	 * Process case errors. If case is valid, assign errors to case tracking entity.
	 * @param fFilePath - Processed file
	 * @return 0= failure, 1= successful
	 */
	public boolean processErrors( ) {
		try {
			logger("Processing validation error(s)");

			// Add eSuite related errors to Tracking entity que + email body
			int iValErrs = 0;
			int iErrCount = aErrorList_.findAll { e -> !e.bProcessed_ }.size();

			// Log all errors
			aErrorList_.findAll { e -> !e.bProcessed_ }.each { ValidationError e ->
				if ( iValErrs == 0 ) {  // new entry?
					logger "Adding $iErrCount tracking/detail error(s)"
					logger e.outputTextErrorHeader();
				}

				// Create error memo for court assignment based on what's available
				StringJoiner sMemo = new StringJoiner(" | ");
				if (e.bReject_)	// rejected?
					sMemo.add("<b>${iTracking_.INTERFACE_TYPE_} Failed</b>");
				else
					sMemo.add("<b>${iTracking_.INTERFACE_TYPE_} Warning</b>");
				if (e.sCaseNum_)  // valid case#?
					sMemo.add("Case#(${e.sCaseNum_})");
				sMemo.add(e.sDesc_);                                     // add error description on end

				// Configure eSuite assignment error type if set
				String sType = iTracking_.TRACKING_DETAIL_INT_REVIEWERR_;  // review error assignment
				if (e.bReject_)	// rejected?
					sType = iTracking_.TRACKING_DETAIL_INT_REJECTERR_;     // reject error assignment

				// Add tracking detail message to trigger assignment queues if needed
				iTracking_.addTrackingDetail(sType, iTracking_.TRACKING_STATUS_ERROR_, "Validation error", sMemo.toString() );

				// Update console output
				logger(iValErrs + 1 + ") " + sMemo);

				iValErrs++;
				e.bProcessed_ = true; // indicate error processed
			}

			// Check error batch for reject
			if( (aErrorList_.findAll { e -> e.bReject_ }.size() > 0) )
				iTracking_.updateResult(iTracking_.RESULT_FAIL_OFS_FAILED_);

			return true;

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::processErrors - Error process handler");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
	}

	/** ----------------------------------------------------------------------------------------------
	 * Finalize script execution by processing errors and sending emails if required
	 * @return nothing
	 */
	public void finalizeScriptExecution() {

		// Process client/server(eSuite) errors
		processErrors();

		// Set tracking result based on script execution
		if ( iTracking_.tracking_.result != iTracking_.RESULT_START_ ) {  // tracking error reported?
			sendEmail();    // send email report for exception/reject errors
		} else {    // success
			iTracking_.updateResult(iTracking_.RESULT_SUCCESS_);

			// If Debug mode send script trace on success
			if( bDebug_ )   // debug?
				sendEmail();
		}

		// Set DEF party document status based on filing condition
		if( !this.bReviewFilingSent_ ) // not sent?
			updateDocumentStatus(cParty_, (String)mDocStatus_.ofsSubmit, (String)mDocStatus_.ofsFailed); // update document status

		// Add interface result trace to tracking object
		iTracking_.addTrackingDetail(iTracking_.TRACKING_DETAIL_INT_LOG_, iTracking_.TRACKING_STATUS_END_,"${iTracking_.INTERFACE_} Log", "Interface results = ${iTracking_.tracking_.result}", sLoggerOutputBuf_.toString());
	}

	/** ------------------------------------------------------------------------------------
	 *  Send Email to distribution list.
	 */
	public void sendEmail() {
		if ( !this.cRule_.mailManager.isMailActive() || iTracking_ == null ) {
			logger("eMail service is disabled");
			return;
		}

		StringBuilder body = new StringBuilder();
		String subject = "$iTracking_.INTERFACE_ BR " + ((bDebug_)?'Debug':'Error');
		SimpleDateFormat dt = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");

		try {
			body.append("Interface: ${iTracking_.INTERFACE_}<br>");
			body.append("Environment: <a href='");
			body.append(cSysProps_.seSuiteEnvURL_);
			body.append("'>");
			body.append(cSysProps_.seSuiteEnvURL_);
			body.append("</a><br>");
			body.append("---------------------------------------------------------<br>");
			body.append("Execution date: ");
			body.append(dt.format(dExecDate_) + "<br>");
			if( !StringUtil.isNullOrEmpty(iTracking_.tracking_.caseNumber) )
				body.append("Case#: ${iTracking_.tracking_.caseNumber}<br>");
			body.append("<br>");

			body.append("Tracking Results:<br>");
			body.append("---------------------------------------------------------<br>");
			body.append("Tracking Id: ${iTracking_.getID()}<br>");
			body.append("Result: ${iTracking_.tracking_.result}<br>");
			if( !StringUtil.isNullOrEmpty(iTracking_.tracking_.exception) )
				body.append("Exception: ${iTracking_.tracking_.exception}<br>");
			body.append("<br>");

			body.append("Interface BR Script Log:<br>");
			body.append("---------------------------------------------------------<br>");
			body.append(sLoggerOutputBuf_.toString());

			// Dispatch emails to email support group
			if( cSysProps_.sEmailList_ != null ) { // valid emails?
				String[] tokens = cSysProps_.sEmailList_.split(',');  // split out emails by ','
				for (String sEmail : tokens) {           // dispatch to all emails
					logger("Sending error report to $sEmail");
					this.cRule_.mailManager.sendMail(sEmail, subject, body.toString());
				}
			}
		}
		catch (Exception ex) {
			// Don't log eMail timeout exception errors in CtInterfaceTracking since it fails
			logger "Exception::sendEmail - eMail timeout error - " + ex.message;
		}
	}

	/** ------------------------------------------------------------------------------------
	 *  Debug console output/logger
	 *  @param sBuf Class:String - input string buffer
	 *  @returns input buffer
	 */
	public String logger(String sBuf) {
		sLoggerOutputBuf_.append(sBuf + "<br>");
		cRule_.logger.debug sBuf;
		return sBuf;
	}
}

/** ------------------------------------------------------------------------------------
 * eSuite validation class method
 */
class ValidationError {
	boolean bReject_= false;      // reject process
	String sFilename_ = "";       // filename
	String sCaseNum_ = "";        // case number
	String sDesc_ = "";           // error description
	Date   dDate_;                // date/time of error
	int    nType_ = 0;            // routing type for workflow queue assignment
	boolean bProcessed_= false;   // processed flag

	// Constructor
	ValidationError( boolean bReject_, String sCaseNum, String sDesc, int nType=0) {
		this.bReject_= bReject_;
		this.sCaseNum_= sCaseNum;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date();    // get current date and time
	}

	// Constructor
	ValidationError( boolean bReject_, String sDesc, int nType=0) {
		this.bReject_= bReject_;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date();   // get current date and time
	}

	// Constructor
	ValidationError( boolean bReject_, String sCaseNum, String sFilename, String sDesc, int nType=0) {
		this.bReject_= bReject_;
		this.sCaseNum_= sCaseNum;
		this.sFilename_= sFilename_;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date();   // get current date and time
	}

	// Error header
	String outputTextErrorHeader() {
		DateFormat df = new SimpleDateFormat("yyyy.MM.dd-HH:mm");
		String sOutput = "OFS RF Error Report - (Date:${df.format(dDate_)}, Case#:$sCaseNum_)";
		return sOutput;
	}

	// Error row
	String outputTextErrorRow() {
		String output = this.sDesc_;
		return output;
	}
}

/** ------------------------------------------------------------------------------------
 *  Class to maintain charge information for missing statutes
 */
class ChargeStatuteObj {
	String sChargeID_;
	String sStatuteSectionNumber_;
	String sStatuteSectionName_;
    List<String> lsStatuteAttributeLst_ = null;

	ChargeStatuteObj( String sChargeID, String sStatuteSectionNumber, String sStatuteSectionName, List<String> lsStatuteAttributeLst=null ) {
		sChargeID_ = sChargeID;
		sStatuteSectionNumber_ = sStatuteSectionNumber;
		sStatuteSectionName_ = sStatuteSectionName;
        if( lsStatuteAttributeLst != null )
            lsStatuteAttributeLst_= lsStatuteAttributeLst;
	}
}

/** ------------------------------------------------------------------------------------------------
 *  Server Message Block (SMB) network file class wrapper for network files and directories.
 *  R.Short BitLink
 */
class SmbFileWrapper {
	private String sSmbURL_ = null;
	private NtlmPasswordAuthentication cSmbNtlmCreds_ = null;
	public SmbFile cSmbFile_;

	// Constructor
	SmbFileWrapper(){}

	// Constructor @Overloaded
	SmbFileWrapper( String sSmbURL, String sUsername, String sPassword, String sDomain ) {
		setAuth( sUsername, sPassword, sDomain );
		smbFile(sSmbURL);
	}

	// Constructor @Overloaded
	SmbFileWrapper( String sUsername, String sPassword, String sDomain = "" ) {
		setAuth( sUsername, sPassword, sDomain );
	}

	// Set network authentication
	public void setAuth( String sUsername, String sPassword, String sDomain = "" ) {
		try {
			this.cSmbNtlmCreds_ = new NtlmPasswordAuthentication(sDomain, sUsername, sPassword);
		} catch (Exception ex) {
			throw new Exception("Exception::setAuth - network credential error, check username/password" + ex.message );
		}
	}

	// Get credentials
	// returns object credentials
	public NtlmPasswordAuthentication getAuth() {
		return this.cSmbNtlmCreds_;
	}

	// Check smbFile connection by simply requesting the path
	// Returns true if successful or false if failed
	public boolean isConnected() {
		boolean bRetVal = false;
		try {
			if (this.cSmbFile_ != null) {
				this.cSmbFile_.getPath();
				bRetVal = true;
			}
		} catch (Exception ex) {
			ex; // ignore getPath errors
		}
		return bRetVal;
	}

	// Build/Validate smbFile network URL
	// Returns formatted server path
	public String toSmbURL( String sSmbURL ) {
		if( !StringUtil.isNullOrEmpty(sSmbURL) ) {  // valid path?

			// SMB paths require Unix style path separators '/', so convert server path if needed
			if( sSmbURL.startsWith("//") )
				sSmbURL = "smb:" + sSmbURL;
			else if ( !sSmbURL.toUpperCase().startsWith("SMB://") ) {
				sSmbURL = sSmbURL.replaceAll("^/*", "").replaceAll("^\\\\*", "");
				sSmbURL = "smb://" + sSmbURL;
			}
			sSmbURL = sSmbURL.replace('\\', '/');

			// SMB workgroups, servers, shares, or directories require a trailing slash '/' if a directory path
			if ( StringUtil.isNullOrEmpty(FilenameUtils.getExtension(sSmbURL))) { // represents directory path w/ no file?
				if (!sSmbURL.endsWith("/"))
					sSmbURL += '/';
			}
		}
		return sSmbURL;
	}

	// Get smbFile URL
	// Returns SmbFile URL or exception if error
	public String getSmbURL() {
		return this.sSmbURL_;
	}

	// Create/Connect smbFile object to network URL
	// Returns true if successful connection or false/exception if error
	public boolean smbFile(String sSmbURL, boolean bMkDirPaths = false) {
		if( cSmbNtlmCreds_ == null || StringUtil.isNullOrEmpty(sSmbURL) )
			throw new Exception("Exception::createFileDirs - NtlmNetworkAuth=null or SmbURL($sSmbURL)=null");

		// Create new SMB network file object
		try {
			this.sSmbURL_= toSmbURL(sSmbURL);
			this.cSmbFile_ = new SmbFile(this.sSmbURL_, cSmbNtlmCreds_);
		} catch (Exception ex) {
			throw new Exception("Exception::smbFile - Error creating network SmbFile instance " + ex.message );
		}

		// Make server paths if required
		if( bMkDirPaths )
			mkDirs();

		return isConnected();
	}

	// Get smbFile object
	// Returns SmbFile object or exception if error
	public SmbFile getSmbFile() {
		return this.cSmbFile_;
	}

	// Create smbFile directories @Overloaded
	// Returns exception if error
	public mkDirs() {
		mkDirs( this.sSmbURL_ );
	}

	// Create smbFile directories.
	// Returns exception if error
	public mkDirs( String sToSmbURL ) {
		if( this.cSmbNtlmCreds_ == null || StringUtil.isNullOrEmpty(sToSmbURL) )
			throw new Exception("Exception::createFileDirs - NtlmNetworkAuth=null or sToSmbURL($sToSmbURL)=null");

		// Extract path if filename attached, if not ignore
		sToSmbURL= this.toSmbURL(sToSmbURL); // convert to smb
		if( !StringUtil.isNullOrEmpty(FilenameUtils.getExtension(sToSmbURL)) ) { // filename?
			sToSmbURL = FilenameUtils.getPath(sToSmbURL); // get rid of filename
		}

		// Format path and build folders if required (URL should not have a filename at this point)
		SmbFile cSmbFile = new SmbFile(this.toSmbURL(sToSmbURL), this.cSmbNtlmCreds_);
		try {
			if (!cSmbFile.exists())
				cSmbFile.mkdirs();
		} catch (Exception ex) {
			throw new Exception("Exception::mkDirs - error creating file directories " + ex.message );
		}
	}

	// Get smbFile into byte array. Use object credentials
	// Returns file byte array or null/exception if error
	public byte[] getFile( String sFromSmbURL ) {
		byte[] fisBytes;
		if( this.cSmbNtlmCreds_ == null || StringUtil.isNullOrEmpty(sFromSmbURL) )
			throw new Exception("Exception::getFile - NtlmNetworkAuth=null or sFromSmbURL($sFromSmbURL)=null");

		// Read network file into file byte stream
		SmbFile cSmbFile = new SmbFile(sFromSmbURL,this.cSmbNtlmCreds_);
		fisBytes = new byte[(int)cSmbFile.length()]; // allocate byte[] for storage
		SmbFileInputStream fis = new SmbFileInputStream(cSmbFile);
		try{
			fis.read(fisBytes); // read in file
		} catch (SmbException ex) {
			throw new Exception("Exception::getFile - error reading ${cSmbFile.getPath()} file" + ex.message)
		} finally {
			if( fis )
				fis.close();
		}
		return fisBytes;
	}

	// Put smbFile to destination smbURL. Use object credentials
	// Returns true if successful or false/esception if error
	public boolean putFile( String sData, String sToSmbURL, boolean bisAppend=false ) {
		boolean bRetVal= false;
		if( this.cSmbNtlmCreds_ == null || StringUtil.isNullOrEmpty(sToSmbURL) )
			throw new Exception("Exception::putFile - NtlmNetworkAuth=null or sToSmbURL($sToSmbURL)=null");

		// Write smb network file
		SmbFile cSmbFile = new SmbFile(sToSmbURL,this.cSmbNtlmCreds_);
		SmbFileOutputStream fos = new SmbFileOutputStream(cSmbFile,bisAppend);
		try{
			fos.write(sData.getBytes()); // write file
			bRetVal= true;
		} catch (SmbException ex) {
			throw new Exception("Exception::putFile - error writing ${cSmbFile.getPath()} file" + ex.message)
		} finally {
			if( fos )
				fos.close();
		}
		return bRetVal;
	}

	// Move source object file to destination network smb path. @Overloaded
	// Returns true if successful or false if failed/exception
	public boolean moveTo(String sToSmbPath) {
		return moveTo(this.cSmbFile_, sToSmbPath);
	}

	// Move file to destination network smb path. Use object credentials
	// Returns true if successful or false if failed/exception
	public boolean moveTo(SmbFile fFromSmbPath, String sToSmbPath) {
		boolean bRetVal = false;
		if (this.cSmbNtlmCreds_ == null || fFromSmbPath == null || StringUtil.isNullOrEmpty(sToSmbPath))
			throw new Exception("Exception::moveTo - NtlmNetworkAuth=null, FromSmbPath=null or sToSmbURL($sToSmbURL)=null");

		try {
			// Make destination folders if needed
			mkDirs(sToSmbPath);

			// Create SMB network file share object for sToSmbPath
			SmbFile fToSmbPath = new SmbFile(this.toSmbURL(sToSmbPath) + fFromSmbPath.getName(), this.cSmbNtlmCreds_);

			// Delete destination file if exists
			if ( fToSmbPath.isFile() ) // yes?
				fToSmbPath.delete();

			// Move/Rename the network file
			fFromSmbPath.renameTo(fToSmbPath);
			bRetVal = true;

		} catch (Exception ex) {
			throw new Exception("Exception::moveTo - file move error - " + ex.message );
		}
		return bRetVal;
	}
}


