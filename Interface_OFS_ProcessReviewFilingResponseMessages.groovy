/** ------------------------------------------------------------------------------------------------------------------
 * Odyssey File and Serve (OFS) review filing response interface - City of Fresno, CA
 *
 * V1.0 by R.Short / Bit Link Solutions on 06/07/2019
 * . Initial release
 *
 * Purpose:
 * The purpose of this interface is to validate/process inbound JSON messages containing reviewFiling submission
 * or notification responses from the Odyssey File and Serve system returned from previous case filings.
 *
 * Business Rule:
 * Code = Interface_OFS_ProcessReviewFilingResponseMessages
 * Name = Interface_OFS_ProcessReviewFilingResponseMessages
 * Category = Interface
 * -----------------------------------------------------------------------
 * Workflow Process1:
 *  Code = INT_OFS_P2caseFilingId
 *  Name = OFS Court Filing - Review
 * Triggers:
 *  Rule: Ct Interface Tracking Detail insert and OFS - Court Filing Review Que
 * ----
 * Work Queue:
 *  Number = INT_OFS_P2.1
 *  Name = OFS Court Filing - Review
 * Work Queue Rule:
 *  Screens = caseNumber=@{eventEntity.interfaceTracking.case}
 * -----------------------------------------------------------------------
 * JSON response inbound message
 * {
 *	 "rfResponse": {
 *	  "ePros": {
 *	    "submitDocRefId": ""
 *	  },
 *	    "reviewFilingResponse": false,
 *	    "caseDocketId": "CV-001194-2015",
 *	    "caseTrackingId": "4322",
 *	  	"caseFilingId": "f786e6d6-558c-448f-b249-067002d716a0",
 *	  	"caseFilingIdText": "FILINGID",
 *	  	"caseFilingDate": "2013-09-10T14:58:33.0Z",
 *	  	"organizationId": "fresno:cr",
 *	  	"filingStatusText": "filing has been accepted by the court",
 *	  	"filingStatusCode": "accepted",
 *	  	"statusErrorList": [],
 *	  	"exception": ""
 *	 }
 * }
 *
 * JSON response output message
 * {
 *   "eResponse": {
 *   "code": "500",
 *   "status": "Internal Server Error",
 *   "message": {
 *   "client": [
 *   ""
 *   ],
 *   "server": [
 *   ""
 *   ]
 *   }
 *   }
 * }
 *
 * ESL to ePros REST Service Call
 * URL = http://fresda-qa.ecourt.com/sustain/ws/rest/ecourt/executeRule
 * SandBox URL = http://daappwebdev:8082/sustain/ws/rest/ecourt/executeRule
 * {
 * "ruleCode": "OFS_INT_ProcessReviewFilingResponseMessages",
 * "inputParams": {
 * "params": [{
 * "name": "rfResponseJson",
 * "value":"{"rfResponse":{"ePros":{"submitDocRefId":""},"reviewFilingResponse":false,"caseDocketId":"CV-001194-2015","caseTrackingId":"4322","caseFilingId":"f786e6d6-558c-448f-b249-067002d716a0","caseFilingIdText":"FILINGID","caseFilingDate":"2013-09-10T14:58:33.0Z","organizationId":"gregg:dc","filingStatusText":"filing has been accepted by the court","filingStatusCode":"accepted","statusErrorList":[],"exception":""}}"
 * }]}
 * }
 *
 * -----------------------------------------------------------------------
 * OFS external variables:
 * @input: arg: _rfResponseJson - Class:String
 *
 * OFS output external variables:
 *  output arg: _eResponse - Class:String
 */

import com.sustain.DomainObject;
import com.sustain.cases.model.*;

import com.sustain.expression.Where;
import com.sustain.lookuplist.model.LookupAttribute;
import com.sustain.lookuplist.model.LookupList;
import com.sustain.properties.model.SystemProperty;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import com.hazelcast.util.StringUtil;

mailManager.sendMail("jcamarena@journaltech.com", "FRESNO DA - OFS Response", _rfResponseJson, null);

/** ------------------------------------------------------------------------------------
 * Interface execution begins here
 */
new ProcessReviewFilingResponseMsgInterface(this).exec();

/** ------------------------------------------------------------------------------------
 * Interface Tracking class
 */
class MyInterfaceTracking {
	CtInterfaceTracking tracking_;

	static final String INTERFACE_ = "Interface_OFS_ProcessReviewFilingResponseMessages";
	static final String INTERFACE_TYPE_ = "OFS_RVWFILE_RESP";	 // process review filing response messages

	// Tracking error result codes [CL_INTERFACE_RESULT]
	static final String RESULT_START_ = "START";
	static final String RESULT_SUCCESS_ = "SUCCESS";
	static final String RESULT_CLIENTFAIL_ = "HTTP_BAD_REQ";
	static final String RESULT_FAILED_ = "FAILED"
	static final String RESULT_FAIL_TRANSMIT_EMAIL_ = "FAIL_TX_EMAIL";
	static final String RESULT_FAIL_EXCEPTION_ = "FAIL_EXCEPTION";

	// Tracking error status codes [CL_TRACKING_STATUS]
	static final String TRACKING_STATUS_END_ = "END";
	static final String TRACKING_STATUS_ERROR_ = "ERROR";
	static final String TRACKING_STATUS_SERVERERR_ = "SVR_ERROR";
	static final String TRACKING_STATUS_CLIENTERR_ = "API_ERROR";

	// Tracking detail type codes [CL_TRACKING_DETAIL]
	static final String TRACKING_DETAIL_INT_REVIEWERR_ = "OFS_RVERR";
	static final String TRACKING_DETAIL_INT_REJECTERR_ = "OFS_RJERR";
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

	String setCaseNumber(String caseNum, Case cCase=null) {
		tracking_.setCaseNumber(caseNum);
		if( cCase != null )
			tracking_.setCase(cCase);
		tracking_.saveOrUpdate();
		return caseNum;
	}

	void setParty(Party cParty) {
		if( cParty != null )
			tracking_.setParty(cParty)
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
		tracking_.setExternalTrackingID(id);
		tracking_.saveOrUpdate();
	}
}

/** ------------------------------------------------------------------------------------
 * ReviewFiling response interface class
 */
public class ProcessReviewFilingResponseMsgInterface {
	public Boolean bDebug_ = true; 			     // debug flag used for script trace reporting
	public Script cRule_;                        // pointer to business rule for logger output

	public List aESuiteErrorList_ = [];          // eSuite error list
	public List aApiErrorList_ = [];             // api error list
	public Date dExecDate_ = new Date();         // get current date

	// Interface defines
	static final String ORGANIZATION_ID_ = "fresno:cr";

	// System Property attribute class
	class MySysProperties {
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

	// Setup response filing type
	/*public enum OFSRespType {
		SubmitReviewFiling,		// quick response type from submission filing
		NotifyReviewFiling		// reject/acceptance notification type, case# will be valid for this type
	}
	public OFSRespType nResponseType_= OFSRespType.SubmitReviewFiling; // set default until the case# known
    */
  	public String nResponseType_ =  "SubmitReviewFiling";

	// Entity attributes
	MyInterfaceTracking iTracking_;        // pointer to tracking interface

	// API Response Object
	ApiResponse cApiResponse_ = new ApiResponse();

	// For debug purposes
	StringBuilder sLoggerOutputBuf_ = new StringBuilder();

	/** ------------------------------------------------------------------------------------
	 * Constructor
	 * @param rule = reference to current business rule for logger output
	 */
	ProcessReviewFilingResponseMsgInterface(Script rule) {
		this.cRule_ = rule
		this.cSysProps_= new MySysProperties();
	}

	/** ------------------------------------------------------------------------------------
	 * Main execution handler
	 */
	public void exec() {
		try {

			// Initialize tracking system
			logger("Script execution started @ ${dExecDate_}");
			iTracking_ = new MyInterfaceTracking(dExecDate_);

			// Get system assignments
			if ( assignSystemProperties() != null ) { // valid?

				// Read and parse JSON data from OFS endpoint / eSuite into a data structure of list tags and maps
				Object reqJsonSluper = null;
				String sJson = groovy.json.StringEscapeUtils.unescapeJava(cRule_._rfResponseJson);	// serialize JSON structure
				if ( (reqJsonSluper = new groovy.json.JsonSlurper().parseText(sJson)) != null ) { // parse structure, no error?
					if (bDebug_) logger groovy.json.JsonOutput.prettyPrint(sJson);

					// Task handler for JSON messages
					logger("Validating/Processing OFS response payload");
					validateAndProcessResponsePayload(reqJsonSluper);

				} else {
					this.aApiErrorList_.add(new ApiError(true, "Invalid JSON response structure format"));
				}
			}

			// Finalize script execution
			logger("Script complete");
			finalizeScriptExecution();

			// Create return json response
			cRule_._eResponse = cApiResponse_.getResponseJson().toString();
			if (bDebug_) logger cApiResponse_.getResponseJson().toPrettyString();

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::exec - Case execution handler");
			iTracking_.updateResult(MyInterfaceTracking.RESULT_FAIL_EXCEPTION_);
		}
	}

	/** -------------------------------------------------------------------------------------------------------
	 * Assign all System Properties required for interface
	 * @returns (0= Failure, 1= Successful)
	 */
	public boolean assignSystemProperties() {
		try {
			logger "Loading system properties";
			cSysProps_.seSuiteEnvURL_ = SystemProperty.getValue("general.serverUrl");
			cSysProps_.sEmailList_ = SystemProperty.getValue("interface.odyssey.emailCsvList");
		} catch ( Exception ex ){
			logger iTracking_.setException(ex.message, "Exception::assignSystemProperties - System property error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
		return true;
	}

	/** -------------------------------------------------------------------------------------------------------
	 * Validate response JSON payload keys/values sent from OFS system. This method only needs to be called when a
	 * payload request is received from OFS to validate keys.
	 * @param cJSluper - response json sluper
	 * @returns 0= failure, 1= successful
	 */
	public boolean validateAndProcessResponsePayload(Object cJSluper) {

		try {
			// -------------------------- Validate rfResponse JSON keys

			logger("Validating rfResponse keys");
			if (cJSluper.rfResponse != null) {   // valid key?
				["reviewFilingResponse", "caseDocketId", "caseTrackingId", "caseFilingId", "caseFilingDate", "organizationId", "filingStatusText", "filingStatusCode", "statusErrorList"].each {
					if (!cJSluper.rfResponse.containsKey(it))  // key missing?
						this.aApiErrorList_.add(new ApiError(true, "rfResponse", logger("Invalid rfResponse.$it key")));
				}
			} else {
				this.aApiErrorList_.add(new ApiError(true, "rfResponse", logger("Missing rfResponse: tag")));
				return false;
			}

			// Validate status error element list
			if (cJSluper.rfResponse.statusErrorList) {   // valid key?
				for (int e = 0; e < (int) cJSluper.rfResponse.statusErrorList.size(); e++) {
					logger("Validating rfResponse.statusErrorList[$e]");
					["statusCode", "statusText"].each {
						if (!cJSluper.rfResponse.statusErrorList[e].containsKey(it))  // key missing?
							this.aApiErrorList_.add(new ApiError(true, "rfResponse", logger("Invalid rfResponse.statusErrorList[e].$it key")));
					}
				}
			}

			logger("Validating rfResponse.ePros keys");
			if (cJSluper.rfResponse.ePros != null) {   // valid key?
				["submitDocRefId"].each {
					if (!cJSluper.rfResponse.ePros.containsKey(it))  // key missing?
						this.aApiErrorList_.add(new ApiError(true, "rfResponse.ePros", logger("Invalid rfResponse.ePros.$it key")));
				}
			} else {
				this.aApiErrorList_.add(new ApiError(true, "rfResponse.ePros", logger("Missing rfResponse.ePros: tag")));
			}

          logger("382:Response message type detected = (${cJSluper.rfResponse.reviewFilingResponse}) " + ((cJSluper.rfResponse.reviewFilingResponse == false) ? "[NotifyFiling]" : "[ReviewFiling]"));
          if (cJSluper.rfResponse.reviewFilingResponse == false){  // notifyFiling?
				nResponseType_ = "NotifyReviewFiling";    // has caseDocketId, set notification type response
          }
logger("386:");
			// Search for any JSON structure reject errors before validating values
			if (this.aESuiteErrorList_.findAll { e -> e.bReject_ && !e.bProcessed_ }.size() > 0 ||
					this.aApiErrorList_.findAll { e -> e.bReject_ && !e.bProcessed_ }.size() > 0) {
				return false;
			}
logger("392:");
			// -------------------------- Validate rfResponse JSON values

			// If notification response, check for exception error here and report it since the notification response
			// will not have any case information if an exception is thrown, so it must be reported here. The submission
			// response will handle the exception error since that message includes a documentId to find a case and
			// report documentStatus information.
			if ( !StringUtil.isNullOrEmpty(cJSluper.rfResponse.exception) && nResponseType_ == "NotifyReviewFiling" ) {
				logger("399: Notify response service report error - " + (String) cJSluper.rfResponse.exception);
				this.aESuiteErrorList_.add(new eSuiteError(true, "Notify response service reported exception - ${cJSluper.rfResponse.exception}"));
				return false;
			}
          logger("404:${cJSluper.rfResponse.documentFileControlID}");
			// Attempt to find the associated case by using the original ePros submitting document.id
          Document cFilingDoc;
          if (cJSluper.rfResponse.documentFileControlID != null){
          cFilingDoc = Document.get(Long.parseLong(cJSluper.rfResponse.documentFileControlID)); logger("408:");
          } else {
           cFilingDoc = Document.get(Long.parseLong(cJSluper.rfResponse.ePros.submitDocRefId)); logger("410:");
          }
          logger("412:");
          
	      Case cCase = cFilingDoc?.case;// getAssociatedCase(cJSluper);
          logger("407:cFilingDoc:${cFilingDoc}; cCase:${cCase}");
			if ( cCase != null ) {    // valid case?
				iTracking_.setCaseNumber(cCase.caseNumber, cCase);    // associate caseNumber / case reference to tracking
			} else {	// error
				this.aESuiteErrorList_.add(new eSuiteError(true, logger("No valid case found for caseDocketId(${cJSluper.rfResponse.caseDocketId}) or submitDocRefId(${cJSluper.rfResponse.ePros.submitDocRefId})") ));
				return false;
			}

			// Find valid ePros document using response submission document reference ID from original filing
			//Document cFilingDoc= Document.get(Long.parseLong(cJSluper.rfResponse.documentFileControlID));// getAssociatedDoc( cCase, cJSluper ); 
			if( cFilingDoc == null ) {
				this.aESuiteErrorList_.add(new eSuiteError(true, cCase.caseNumber, logger("No valid case filing document found for filing response")));
				return false;
			}
			logger("419:Updating document w/ OFS filing documentId(${cFilingDoc.id})");

			// -------------------------- Process notify review response

			if( nResponseType_ == "NotifyReviewFiling" ) {    // notify response type?
				logger("424:<b>Processing aSync review notification response message</b>");

				// Test for correct organization ID
				logger("427;Validating organizationId - ${cJSluper.rfResponse.organizationId}");
              if (cJSluper.rfResponse.organizationId != ORGANIZATION_ID_){
					this.aESuiteErrorList_.add(new eSuiteError(false, logger("Response organizationId(${cJSluper.rfResponse.organizationId}) != $ORGANIZATION_ID_")));
              }
              logger("431 cJSluper.rfResponse.caseTrackingId: ${cJSluper.rfResponse.caseTrackingId}")
				// Update case w/ the court caseDocketId and caseTrackingId if available from notification response
			    if( !StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseTrackingId) ) {
					List<OtherCaseNumber> lOthCasNbr = cCase.collect("otherCaseNumbers[type=='CRT' && ((memo == null || memo.isEmpty()) || memo == #p1)]", "${cJSluper.rfResponse.filingCaseTitleText}");
					OtherCaseNumber cOthCasNbr = lOthCasNbr.last() ?: new OtherCaseNumber();
                    logger("Add or Update CRT Number");
					// Add other case attributes
                    cOthCasNbr.memo = cOthCasNbr.memo == null || cOthCasNbr.memo.trim().isEmpty() ? "${cJSluper.rfResponse.filingCaseTitleText}" : cOthCasNbr.memo;
					cOthCasNbr.type = 'CRT';
					cOthCasNbr.cf_OFSCaseTrackingID = cJSluper.rfResponse.caseTrackingId;
					cOthCasNbr.case = cCase;
					if ( !StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseDocketId) ) // valid court#?
						cOthCasNbr.number = cJSluper.rfResponse.caseDocketId;
	 			    else
						this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, logger("No caseDocketId found in review notification response message")));

					if (lOthCasNbr.empty)    // Add new instance else update
						cCase.otherCaseNumbers.add(cOthCasNbr);

					logger(((lOthCasNbr.empty)?"Adding":"Updating") + " Case.OtherCaseNumber w/ caseDocketId(${cJSluper.rfResponse.caseDocketId}) and caseTrackingId(${cJSluper.rfResponse.caseTrackingId})");
					cCase.saveOrUpdate();    // commit it
				} else
					this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, logger("No caseTrackingId found in review notification response message")));

				// Test for valid status code, if no attribute found just use default
				String sStatusCode = null;
				if ((sStatusCode = validateLookupCode(cJSluper.rfResponse.filingStatusCode, "DOCUMENT_STATUS")) == null) {  // not found, report it and bail?
					this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, logger("No notification filing response statusCode(${cJSluper.rfResponse.filingStatusCode}) found in [DOCUMENT_STATUS] lookupList")));
					sStatusCode = cJSluper.rfResponse.filingStatusCode;
				}

				// Find latest status w/ received by court or create new instance if not found just in case
				boolean bIsNewDocStatus = false;
			  DocumentStatus cDocStat = getLatestDocumentStatus(cFilingDoc, (String)mDocStatus_.ofsRecv, cJSluper.rfResponse.caseFilingId);
              //DocumentStatus cDocStat = Document.get(Long.parseLong(cJSluper.rfResponse.documentFileControlID));
              logger("465: cDocStat: ${cDocStat}; cJSluper.rfResponse.caseFilingId: ${cJSluper.rfResponse.caseFilingId}")
				if( cDocStat == null ) {
					logger("465: StatusType(${mDocStatus_.ofsRecv}) not found, adding new documentStatus; caseTrackingId:${cJSluper.rfResponse.caseTrackingId}");
					cDocStat = new DocumentStatus();
					bIsNewDocStatus= true;
				}

				// Set documentStatus attributes
				logger("<b>Notification results - [${sStatusCode} | ${cJSluper.rfResponse.filingStatusText}]</b>");
				cDocStat.beginDate = convJsonDateToJavaDate(cJSluper.rfResponse.docFiledDateTime);
                cDocStat.beginDate = cDocStat.beginDate != null ?: java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
                cDocStat.memo = "${cJSluper.rfResponse.filingStatusText}".toString();
                //cDocStat.cf_OdysseyFilingEnvelope = "${cJSluper.rfResponse.filingStatusText}".toString();
                cDocStat.cf_OdysseyFilingCaseTitle = cDocStat.cf_OdysseyFilingCaseTitle == null || cDocStat.cf_OdysseyFilingCaseTitle.trim().isEmpty() ?  "${cJSluper.rfResponse.filingCaseTitleText}" : cDocStat.cf_OdysseyFilingCaseTitle;
				cDocStat.statusType = sStatusCode;
				cDocStat.document= cFilingDoc;
                cDocStat.cf_OdysseyFilingDocId = cDocStat.cf_OdysseyFilingDocId == null || cDocStat.cf_OdysseyFilingDocId.isEmpty() ? cJSluper.rfResponse.caseFilingId : cDocStat.cf_OdysseyFilingDocId ;
              String filingReviewCommentsText = cJSluper.rfResponse.filingReviewCommentsText;
              String newCourtNumber;
if (filingReviewCommentsText.contains("Case Number") || filingReviewCommentsText.contains("CaseNumber")){
  newCourtNumber = filingReviewCommentsText.split("Number")[1]
  newCourtNumber = newCourtNumber.replaceAll("\\.","");
  //cOthCasNbr.sourceCaseNumber = newCourtNumber;
  List<OtherCaseNumber> newCourtNumberIssuedList = cCase.collect("otherCaseNumbers[type=='CRT' && updateReason == 'CRTComment' && number == #p1 && memo == #p2]", newCourtNumber, cJSluper.rfResponse.filingCaseTitleText);
					OtherCaseNumber newCourtNumberIssued = newCourtNumberIssuedList.last() ?: new OtherCaseNumber();
                    newCourtNumberIssued.case = cCase;
                    newCourtNumberIssued.type = "CRT";
                    newCourtNumberIssued.number = newCourtNumber.trim();
                    newCourtNumberIssued.memo = cJSluper.rfResponse.filingCaseTitleText;
                    newCourtNumberIssued.updateReason = "CRTComment";
                    newCourtNumberIssued.saveOrUpdate();
  if (cCase.collect("otherCaseNumbers[number == #p1]", newCourtNumber).isEmpty()){
    cCase.otherCaseNumbers.add(newCourtNumberIssued)
  }
}
              logger("487:newCourtNumber:${newCourtNumber}")
				// Add/Update document status entity
				if( bIsNewDocStatus ) // add?
					cFilingDoc.statuses.add(cDocStat);
				cDocStat.saveOrUpdate();

			// -------------------------- Process submit review response

			} else { // default to submit response type
				logger("486:<b>Processing submit review response message</b>");

				// Collect all errors for reporting
				String sStatusErrorCode= 99;
				StringJoiner statusErrorCsv = new StringJoiner(", ");
				if( StringUtil.isNullOrEmpty(cJSluper.rfResponse.exception) ) { // no exception
					if (cJSluper.rfResponse.statusErrorList != null) { // valid?
						logger("Processing ${cJSluper.rfResponse.statusErrorList.size()} status error(s)");
						sStatusErrorCode = cJSluper.rfResponse.statusErrorList[0].statusCode;

						// Report EFM filing errors and create error list for document tracking
						for (int e = 0; e < cJSluper.rfResponse.statusErrorList.size(); e++) {
							String sEFMError = "EFM filing error ${e+1} - [${cJSluper.rfResponse.statusErrorList[e].statusCode}] | ${cJSluper.rfResponse.statusErrorList[e].statusText}";
							if( sStatusErrorCode != '0' ) // assign only errors to court review
								this.aESuiteErrorList_.add(new eSuiteError(true, cCase.caseNumber, sEFMError));
							logger(sEFMError);
							statusErrorCsv.add("[${cJSluper.rfResponse.statusErrorList[e].statusCode}] | ${cJSluper.rfResponse.statusErrorList[e].statusText}");
						}
					} else
						this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, (String) logger("No EFM status error codes reported in filing response")));

				} else { // exception?
					logger("Submit response ESL service reported exception error - " + (String)cJSluper.rfResponse.exception);

					// Exception messages are generally long, so just use the first 100 bytes if longer for assignments
					String sExceptionError= (String)cJSluper.rfResponse.exception;
					if ( sExceptionError?.length() > 100 )
						sExceptionError = sExceptionError.substring(0, 100) + "..."
					this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, "ESL service reported exception error - " + sExceptionError));
					statusErrorCsv.add(sExceptionError);
				}

				// Search for 'OFSSUB' status to update w/ response message, if not found create new instance just in case
              boolean bIsNewDocStatus = false; 
				DocumentStatus cDocStat = getLatestDocumentStatus(cFilingDoc, (String)mDocStatus_.ofsRecv, cJSluper.rfResponse.caseFilingId);
              logger("525: cDocStat:${cDocStat}; cJSluper.rfResponse: ${cJSluper.rfResponse.caseFilingId}");
				if( cDocStat == null ) {
					logger("527:Adding new documentStatus, statusType(${mDocStatus_.ofsSubmit}) not found");
					cDocStat = new DocumentStatus();
					bIsNewDocStatus= true;
				}

				// Update document status attributes on successful transactions
				if( sStatusErrorCode == '0' && StringUtil.isNullOrEmpty(cJSluper.rfResponse.exception) ) {  // EFM errors/exceptions?
					cDocStat.statusType = mDocStatus_.ofsRecv;   // set received by court

					// Test for correct organization ID (Only valid if not EFM errors)
					logger("Validating organizationId - ${cJSluper.rfResponse.organizationId}");
					if (cJSluper.rfResponse.organizationId != ORGANIZATION_ID_)
						this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, logger("Response organizationId(${cJSluper.rfResponse.organizationId}) != $ORGANIZATION_ID_")));

					// Test for valid caseFilingID (Only valid if not EFM errors)
					logger("Validating caseFilingId - ${cJSluper.rfResponse.caseFilingId}")
                  if (!StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseFilingId)){ // valid
                    logger("544: cf_OdysseyFilingDocId");
						cFilingDoc.cf_OdysseyFilingDocId = cJSluper.rfResponse.caseFilingId;

                        cDocStat.cf_OdysseyFilingDocId = cJSluper.rfResponse.caseFilingId;
                    logger("cFilingDoc.cf_OdysseyFilingDocId:${cFilingDoc.cf_OdysseyFilingDocId}; cDocStat.cf_OdysseyFilingDocId:${cDocStat.cf_OdysseyFilingDocId}; cJSluper.rfResponse.caseFilingId:${cJSluper.rfResponse.caseFilingId}");
                    cDocStat.memo = "${cJSluper.rfResponse.filingStatusText}".toString();
                    cDocStat.cf_OdysseyFilingEnvelope = "${cJSluper.rfResponse.filingEnvelopeId}".toString();
                    cDocStat.sourceCaseNumber = "${cJSluper.rfResponse.filingDefendantFullName}".toString();
                    cDocStat.cf_OdysseyFilingCaseTitle = cDocStat.cf_OdysseyFilingCaseTitle == null || cDocStat.cf_OdysseyFilingCaseTitle.trim().isEmpty() ?  "${cJSluper.rfResponse.filingCaseTitleText}" : cDocStat.cf_OdysseyFilingCaseTitle;
                    //logger("552: " +cJSluper.rfResponse.filingDocuments.getClass());

                  }
                  else {
						this.aESuiteErrorList_.add(new eSuiteError(false, cCase.caseNumber, logger("No valid caseFilingId(${cJSluper.rfResponse.caseFilingId}) found in ofs response")));
                  }

                } else{
					cDocStat.statusType= mDocStatus_.ofsFailed; // set received failed
                }
				logger("Updating documentStatus w/ statusType(${cDocStat.statusType})");
				cDocStat.beginDate = new Date();
				cDocStat.document= cFilingDoc;

				// Add/Update document status entity
              if( bIsNewDocStatus ){ // add?
					cFilingDoc.statuses.add(cDocStat);
              }
				cDocStat.saveOrUpdate();
              if (cJSluper.rfResponse?.filingDocumentsGUID != null && cDocStat.cf_OdysseyFilingData1 == null){
                cDocStat.cf_OdysseyFilingData1 = cJSluper.rfResponse?.filingDocumentsGUID;
              }
              if (cJSluper.rfResponse?.filingDocuments != null && cDocStat.cf_OdysseyFilingData3 == null){
                cDocStat.cf_OdysseyFilingData3 = cJSluper.rfResponse?.filingDocuments;
              }
              if (cDocStat.cf_OdysseyFilingData2 == null){
                cDocStat.cf_OdysseyFilingData2 = cJSluper.rfResponse;
              }
                if (cJSluper.rfResponse?.filingDocuments != null && cDocStat.collect("xrefs[entityType=='Document' and refType=='FILING']").isEmpty()){
                      cJSluper.rfResponse?.filingDocuments?.each({
                        thisFiledDocument -> 
                        logger("Doc " +Document.get(Long.parseLong(thisFiledDocument)));
                        try{
                          cDocStat.addCrossReference(Document.get(Long.parseLong(thisFiledDocument)), "FILING");
                          if (cDocStat.cf_OdysseyFilingDocs == null){
                          cDocStat.cf_OdysseyFilingDocs = Document.get(Long.parseLong(thisFiledDocument))?.title;
                          } else {
                            cDocStat.cf_OdysseyFilingDocs += ", " + Document.get(Long.parseLong(thisFiledDocument))?.title;
                          }
                          cDocStat.saveOrUpdate();
                        }catch(Exception ex){
                          logger("578:Ex: ${ex.getMessage()} ${ex.getCause()}")
                        }
                      });
                    }
				// Add documentTracking filing code/status
				if( sStatusErrorCode != '0' ) { // failed
					logger("Adding new documentTracking entry w/ status(FAILED)");
					DocumentTracking cDocTrk = new DocumentTracking();
					cDocTrk.type = 'ODYFS';
					cDocTrk.status= "FAILED";

					cDocTrk.memo= statusErrorCsv;

					// Add new status entity to document.statuses
					cDocTrk.document= cFilingDoc;
					cFilingDoc.trackings.add(cDocTrk);
				}
			}

			// Save document
			cFilingDoc.saveOrUpdate();	// commit the status

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::validateAndProcessResponsePayload - ReviewFiling validate/Process error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
	}

	/** ----------------------------------------------------------------------------------------------
	 * Get latest document status w/ input parameter
	 * @param cDocument
	 * @param sWith - Find with
	 * @Returns - latest documentStatus or null if error
	 */
	public DocumentStatus getLatestDocumentStatus( Document cDoc, String sWith, String caseTrackingId ){
      if( cDoc == null ){
			return null;
      }
		try {
			logger "597: Searching for latest documentStatus w/ statusType = [$sWith]"

			//DocumentStatus cDocStatus = cDoc.collect("statuses[cf_OdysseyFilingDocId == #p1]", caseTrackingId)?.orderBy("lastUpdated")?.find({thisObject -> thisObject != null});
            DocumentStatus cDocStatus = DomainObject.find(DocumentStatus.class, "document", cDoc, "cf_OdysseyFilingDocId", caseTrackingId.toString())?.sort({a,b -> a.id <=> b.id})?.find({thisObject -> thisObject != null});
          
          logger("cDoc: ${cDoc}; statuses: ${cDoc.collect("statuses.cf_OdysseyFilingDocId")} ;${caseTrackingId.toString()}")
			if( cDocStatus != null ) {
				logger "600: Found status w/ $sWith set on doc, returning latest on ${cDocStatus?.dateCreated}";
				return cDocStatus;
            } else{
				logger "603: No documentStatuses found w/ $sWith"
            }
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::updateDocumentStatus - Error finding latest documentStatus w/ $sWith");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return null;
	}

	/** ------------------------------------------------------------------------------------
	 * Search case for valid document using either the case filing ID or the submitting document
	 * ID. The case filing ID will not valid on new filing submissions.
	 * @param cCase - Active case
	 * @param cJSluper - Response json sluper
	 * @returns - Valid document or returns null if not found
	 */
	 public Document getAssociatedDoc( Case cCase, Object cJSluper ) {
		 Document cDoc = null;
		 try {

			 // Search for valid document using case filing ID
			 if( !StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseFilingId) ) { // valid?
				 logger("637: Searching for original document filing based on case filing Id:(${cJSluper.rfResponse.caseFilingId})");
				 cDoc = (Document) cCase.collect("documents.statuses[cf_OdysseyFilingDocId==#p1]", cJSluper.rfResponse.caseFilingId)?.document?.sort({a,b -> b.id <=> a.id}).find({thisDoc -> thisDoc != null});
			 }

			 // Search for valid document using submitting document ID from original filing
			 if( cDoc == null && !StringUtil.isNullOrEmpty(cJSluper.rfResponse.ePros.submitDocRefId) ) { // valid?
				 logger("643: Searching for original document filing based on submitting document Id(${cJSluper.rfResponse.ePros.submitDocRefId})");
				 cDoc = (Document) cCase.collect("documents[id==#p1]", cJSluper.rfResponse.ePros.submitDocRefId.toLong())?.last();
			 }
           if (cDoc == null){
             cDoc = Document.get(Long.parseLong(cJSluper.rfResponse.documentFileControlID));
           }

		 } catch (Exception ex) {
			 logger iTracking_.setException(ex.message, "Exception::getAssociatedDoc - Document association error");
			 iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		 }

		 return cDoc;
	 }

	/** ------------------------------------------------------------------------------------
	 * Search ePros for a valid case using a series of various searches. If submitDocRefID is
	 * available try that first, if not found, then try the cf_OdysseyFilingDocId, if that's not
	 * found, then try the caseTrackingID.
	 * @param cJSluper - Response json sluper
	 * @Returns - Valid case or returns null if no case found
	 */
	public Case getAssociatedCase( Object cJSluper ) {
		Case cCase= null;
		try {

			// Attempt to find the associated case by using original ePros submitting document.id for first time filings
			if( !StringUtil.isNullOrEmpty(cJSluper.rfResponse.ePros.submitDocRefId) ) { // invalid?
				logger("Searching for case using documentId(${cJSluper.rfResponse.ePros.submitDocRefId})");
				Where w = new Where().addEquals('documents.id', cJSluper.rfResponse.ePros.submitDocRefId.toLong() );
				cCase = DomainObject.find(Case.class, w, maxResult(1))[0];
				if (cCase != null ) // valid?
					logger("Found Case#($cCase.caseNumber) associated w/ submitting document.id");
				DomainObject.clearCache();
			}

			// Attempt to find the associated case by using the case filing identificationId
			if( cCase == null && !StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseFilingId) ) { // invalid?
				logger("Searching for case using caseFilingId(${cJSluper.rfResponse.caseFilingId})");
				Where w = new Where().addEquals('documents.cf_OdysseyFilingDocId',cJSluper.rfResponse.caseFilingId );
				cCase = DomainObject.find(Case.class, w, maxResult(1))[0];
				if (cCase != null ) // valid?
					logger("Found Case#($cCase.caseNumber) associated w/ document filing id");
				DomainObject.clearCache();
			}
          
          	// Attempt to find the associated case by using the case filing identificationId on DocumentStatus
			if( cCase == null && !StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseFilingId) ) { // invalid?
				logger("880: Searching for case using caseFilingId(${cJSluper.rfResponse.caseFilingId})");
				Where w = new Where().addEquals('cf_OdysseyFilingDocId',cJSluper.rfResponse.caseFilingId );
				DocumentStatus thisDocStatus = DomainObject.find(DocumentStatus.class, w, maxResult(1))[0]; logger("682: thisDocStatus: ${thisDocStatus}")
              logger("720:cJSluper.rfResponse.documentFileControlID:${cJSluper.rfResponse.documentFileControlID}")
              cCase = Document.get(Long.parseLong(cJSluper.rfResponse.documentFileControlID))?.case;
              
              if (thisDocStatus != null){
                cCase = thisDocStatus.document.case;
              }
				if (cCase != null ) // valid?
					logger("Found Case#($cCase.caseNumber) associated w/ document filing id");
				DomainObject.clearCache();
			}

			// Attempt to find the associated case by using the case tracking ID
			if ( cCase == null  && !StringUtil.isNullOrEmpty(cJSluper.rfResponse.caseTrackingId) ) { // valid?
				logger("Searching for case using OFS caseTrackingID(${cJSluper.rfResponse.caseTrackingId})");
				Where w = new Where().addEquals('otherCaseNumbers.cf_OFSCaseTrackingID', cJSluper.rfResponse.caseTrackingId );
				cCase = DomainObject.find(Case.class, w, maxResult(1))[0];
				if ( cCase != null ) // valid?
					logger("Found Case#($cCase.caseNumber) w/ associated caseTrackingID");
				DomainObject.clearCache();
			}
          logger("708: cCase: ${cCase}");
		} catch ( Exception ex ){
			logger iTracking_.setException(ex.message, "Exception::getAssociatedCase - Case association error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return cCase;
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
					sRetVal = lAttrib.last().getName()  // get name/code for value
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

	/** ------------------------------------------------------------------------------------
	 * Search eSuite for valid case number and return case.
	 * @param sCaseNumber - case number
	 * Returns - case for CaseNumber. Otherwise returns null
	 */
	public Case getCase(String sCaseNumber) {
		if ( sCaseNumber != null ) return null;
		List<Case> cas = DomainObject.find(Case.class, 'caseNumber', '=', sCaseNumber, maxResult(1));
		return (cas.isEmpty()) ? null : cas.last();
	}

	/** ------------------------------------------------------------------------------------
	 * Convert JSON ISO 8601 date format to standard java date format w/ timezone.
	 * @param sJsonDate - ISO 8601 date format
	 * @returns - Standard java date if valid. Otherwise null
	 */
	public Date convJsonDateToJavaDate(String sJsonDate) {
		if ( sJsonDate == null ) return null;
		Date dDate = null;
		try {
			// Convert ISO 8601 formatted data /w timezone
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			dDate = sdf.parse(sJsonDate);
			logger "Converted Json ISO-8601 Date $sJsonDate to $dDate";
			return dDate;
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::convJsonDateToJavaDate - JSON date to Java date error");
			_iTtracking.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return null;
		}
	}

	/** ----------------------------------------------------------------------------------------------
	 * Process API/eSuite errors. If case is valid, assign errors to case tracking entity.
	 * @returns 0= failure, 1= successful
	 */
	public boolean processErrors() {
		try {
			logger("Processing API/eSuite interface reject/validation error(s)");

			int iValErrs = 0;
			int ieSuiteErrs = 0;
			int iClientErrs = 0;

			// Add eSuite related errors to Tracking entity que + email body
			if ( (ieSuiteErrs = aESuiteErrorList_.findAll { e -> !e.bProcessed_ }.size()) > 0 ) { // errors?
				aESuiteErrorList_.findAll { e -> !e.bProcessed_ }.each { eSuiteError e ->
					if ( iValErrs == 0 ) {  // new entry?
						logger "Adding $ieSuiteErrs tracking/detail error(s)";
						logger e.outputTextErrorHeader();
					}

					// Create error memo for court assignment based on what's available
					StringJoiner sMemo = new StringJoiner(" | ");
					if ( nResponseType_ == "NotifyReviewFiling" )
						sMemo.add("<b>${iTracking_.INTERFACE_TYPE_} " + ((e.bReject_)?"Failed":"Warning") + "</b>" );
					else
						sMemo.add("<b>${iTracking_.INTERFACE_TYPE_} " + ((e.bReject_)?"Failed":"Warning") + "</b>");
					if ( !StringUtil.isNullOrEmpty(e.sCaseNbr_) )  					// valid filing#?
						sMemo.add("Case#(${e.sCaseNbr_})");
					sMemo.add(e.sDesc_);                                     	  	// add error description on end

					// Configure eSuite assignment error type if set
					String sType = iTracking_.TRACKING_DETAIL_INT_REVIEWERR_;  		// review assignment error?
					if (e.bReject_)   // rejected?
						sType = iTracking_.TRACKING_DETAIL_INT_REJECTERR_;          // reject error assignment

					// Add tracking detail message to trigger assignment queues if needed
					iTracking_.addTrackingDetail(sType, iTracking_.TRACKING_STATUS_ERROR_, "Validation error", sMemo.toString() );

					// Update console output
					logger(iValErrs + 1 + ") " + sMemo.toString() );

					// Create a list of server errors for json response{"server":['err1','err2',...]}
					cApiResponse_.aServerErrorMap_.add(e.sDesc_);

					iValErrs++;
					e.bProcessed_ = true; // indicate error processed
				}
			}

			// Add response client API related errors to response list
			iValErrs = 0;
			if ( (iClientErrs = aApiErrorList_.findAll { e -> !e.bProcessed_ }.size()) > 0 ) {
				logger "Adding $iClientErrs api errors to response message"
				aApiErrorList_.findAll { e -> !e.bProcessed_ }.each { e ->
					if ( iValErrs == 0 ) // first one?
						logger e.outputTextErrorHeader();

					// Create a list of client errors for json response{"server":['err1','err2',...]}
					cApiResponse_.aClientErrorMap_.add(e.sMessage_);

					// Configure eSuite assignment error for API type if set
					String sType = iTracking_.TRACKING_DETAIL_INT_REVIEWERR_;  		// review assignment error?
					if ( e.bReject_ ) // rejected?
						sType = iTracking_.TRACKING_DETAIL_INT_REJECTERR_;      // reject error assignment

					// Add API tracking detail
					iTracking_.addTrackingDetail(sType, iTracking_.TRACKING_STATUS_CLIENTERR_, "API error", "$e.sNode_ - $e.sMessage_" );

					// Update console output
					logger(iValErrs + 1 + ") " + e.outputTextErrorRow());

					iValErrs++;
					e.bProcessed_ = true; // indicate error processed
				}
			}

			/** ---------------------------------------------------------------------------------
			 * Set API responseCode based client errors only since ELS service doesn't know how
			 * to handle Server type errors.
			 */
			cApiResponse_.setCode(apiCodes.ok_);                                     // set default response code
			if (aApiErrorList_.findAll { e -> e.bReject_ }.size() > 0)               // client API reject errors?
				cApiResponse_.setCode(apiCodes.clientError_);                        // set to client error
			if (aESuiteErrorList_.findAll { e -> e.bReject_ }.size() > 0)            // server reject errors?
				cApiResponse_.setCode(apiCodes.serverError_);                        // set to server error

			return true;
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::processErrors - Error process handler");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
	}

	/** ----------------------------------------------------------------------------------------------
	 * Finalize script execution by processing errors and sending emails if required
	 * @returns nothing
	 */
	public void finalizeScriptExecution() {

		// Process client/server(eSuite) errors
		processErrors();

		// Set tracking result based on script execution
		if ( iTracking_.tracking_.result != iTracking_.RESULT_FAIL_EXCEPTION_ ) {     // no exception error?
			switch( cApiResponse_.getCode() ) {
				case apiCodes.ok_:
					iTracking_.updateResult(iTracking_.RESULT_SUCCESS_);

					// If Debug mode send script trace on success
					if( bDebug_ )   // debug?
						sendEmail();
					break;

				case apiCodes.clientError_:
					iTracking_.updateResult(iTracking_.RESULT_CLIENTFAIL_);
					sendEmail();
					break;

				case apiCodes.serverError_:
					cApiResponse_.setCode(apiCodes.ok_); // don't report server errors back to API, just report to ePros
					iTracking_.updateResult(iTracking_.RESULT_FAILED_);	// Report failed
					sendEmail();
					break;
			}
		} else {    // exception error
			cApiResponse_.setCode(apiCodes.serverError_);         // force server response error
			cApiResponse_.aServerErrorMap_.add(iTracking_.tracking_.memo);   // set exception info
            sendEmail();    // send email report for exception errors
		}

		// Add interface result trace to tracking object
		iTracking_.addTrackingDetail(iTracking_.TRACKING_DETAIL_INT_LOG_, iTracking_.TRACKING_STATUS_END_,"${iTracking_.INTERFACE_} Log", "Interface results = ${iTracking_.tracking_.result}",sLoggerOutputBuf_.toString());
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
 * API response codes
 */
public class apiCodes {
	public static final int ok_ = 200;           // valid client request
	public static final int clientError_ = 400;  // remote client error
	public static final int serverError_ = 500;  // internal server error
}

/** ------------------------------------------------------------------------------------
 *  API response class
 */
public class ApiResponse {
	public  int iCode_= 500;
	public List aClientErrorMap_ = [];    // Client response map
	public List aServerErrorMap_ = [];    // Server response map

	// Constructor
	ApiResponse() {}

	// Helper functions
	public setCode( int iCode_ ){ this.iCode_= iCode_; }
	public int getCode(){ return iCode_; }

	// Set response status
	private String getStatus() {
		String sStatus = "Internal Server Error";     // set server error default

		// Assign response status based on code
		switch( this.iCode_ ) {
			case apiCodes.ok_:                        // 200
				sStatus = "OK";
				break;
			case apiCodes.clientError_:               // 400
				sStatus = "Bad Request";
				break;
			case apiCodes.serverError_:               // 500 eCourt type errors
				sStatus= "Internal Server Error";
				break;
		}
		return sStatus;
	}

	// Create and return json formatted response structure
	public Object getResponseJson() {
		Object cBuilder = new groovy.json.JsonBuilder();
		cBuilder {
			eResponse {
				"code" iCode_.toString();
				"status" this.getStatus();
				message {
					client aClientErrorMap_.collect { it }
					server aServerErrorMap_.collect { it }
				}
			}
		}
		return cBuilder;
	}
}

/** ------------------------------------------------------------------------------------
 *  API error class method. By default all api errors are sent in response json regardless
 *  of the nType param.
 */
public class ApiError {
	boolean bReject_ = false;        // reject process (false=200 Ok, true=400 client error)
	public String sNode_ = "";
	public String sMessage_ = "";
	boolean bProcessed_ = false;     // processed flag
	Date dDate_;                     // date/time of error
	int nType_ = 0;                  // routing type

	// Basic API error constructor
	ApiError(boolean bReject, String sMessage, int nType=0) {
		this.bReject_= bReject;
		this.sMessage_ = sMessage;
		this.nType_= nType;
		this.dDate_ = new Date();             // get current date and time
	}

	// Node/Key API error constructor
	ApiError(boolean bReject, String sNode, String sMessage, int nType=0) {
		this.bReject_= bReject;
		this.sNode_ = sNode;
		this.sMessage_ = sMessage;
		this.nType_= nType;
		this.dDate_ = new Date();             // get current date and time
	}

	// Error header
	String outputTextErrorHeader() {
		DateFormat df = new SimpleDateFormat("yyyy.MM.dd-HH:mm");
		String sOutput = "OFS Error Report - (Date:${df.format(dDate_)})";
		return sOutput;
	}

	// Error row
	String outputTextErrorRow() {
		String output = "";
		if( sNode_ )
			output = "$sNode_, $sMessage_";
		else
			output = sMessage_;
		return output;
	}
}

/** ------------------------------------------------------------------------------------
 * eSuite validation class method
 */
class eSuiteError {
	boolean bReject_ = false;      // reject process (false=200 Ok, true=500 server error)
	String sCaseNbr_ = "";     	   // case number
	String sDesc_ = "";            // error description
	Date dDate_;                   // date/time of error
	int nType_ = 0;                // routing type for workflow queue assignment + email & file
	boolean bProcessed_ = false;   // processed flag

	// Constructor
	eSuiteError(boolean bReject, String sCaseNbr, String sDesc, int nType= 0) {
		this.bReject_ = bReject;
		this.sCaseNbr_ = sCaseNbr;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date();        // get current date and time
	}

	// Constructor
	eSuiteError(boolean bReject, String sDesc, int nType= 0) {
		this.bReject_ = bReject;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date();       // get current date and time
	}

	// Error header
	String outputTextErrorHeader() {
		DateFormat df = new SimpleDateFormat("yyyy.MM.dd-HH:mm");
		String sOutput = "<b>Error Report - (Date:${df.format(dDate_)}, Case#:$sCaseNbr_)</b>";
		return sOutput;
	}

	// Error row
	String outputTextErrorRow() {
		String output = sDesc_;
		return output;
	}
}


File ofs = new File("\\\\torreypines\\OFS\\out\\queued");
for (file in ofs.listFiles()){
  if (new java.sql.Timestamp(file.lastModified()).before(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(5L)))){
    org.apache.commons.io.FileUtils.moveFileToDirectory(file, new File("\\\\torreypines\\OFS\\out\\processed"), false);
  }
}



