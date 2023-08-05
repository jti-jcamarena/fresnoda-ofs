/** ------------------------------------------------------------------------------------------------------------------
 * Odyssey File and Serve (OFS) Integration Publisher Interface - City of Fresno, CA
 *
 * V1.0 by R.Short / Bit Link Solutions on 4/30/2019
 * . Initial release
 *
 * Purpose:
 * The purpose of this interface is to monitor the Odyssey IntegrationPublisher service for exported sentencing files.
 * When select sentencing information is saved in the CourtÃ¢â‚¬â„¢s Odyssey system, the Odyssey IntegrationPublisher service
 * will send an XML export of information to a custom SOAP endpoint for the eslService to pickup and forward to eProc.
 *
 * Business Rule:
 * Code = Interface_OFS_ProcessIntegrationPublisherMessages
 * Name = Interface_OFS_ProcessIntegrationPublisherMessages
 * Category = Interface
 * -----------------------------------------------------------------------
 * Workflow Process1:
 *  Code = INT_OFS_P2
 *  Name = Probation Inbound Interface
 * Triggers: 8:00 PM Ã¢â‚¬â€œ 5:00 AM, every day
 *  Time:
 * ----
 * Work Queue:
 *  Number = INT_OFS_P2.1
 *  Name = Integration Publisher Inteface
 *  Rule = Interface_OFS_ProcessIntegrationPublisherMessages
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
 *  @input: _eslInboundQueueSmbPath - Class:String - \\\\torreypines\\IP\\inbound\\Prodqueued
 *  @input: _inboundProcessedSmbPath - Class:String - \\\\torreypines\\IP\\inbound\\Prodprocessed
 *  @input: _inboundFailedSmbPath - Class:String - \\\\torreypines\\IP\\inbound\\Prodfailed
 *  @input: _excludeCaseTypes - Class:String[] - AT, JT, ATO, I, NYF, SW, JUVD, J6, CRWR, J6V,  A, 828, AC, TI, J6F, MIS, IJ, PRA, CRSP, AIC, J6W, NMD, SARB, J6R, CRRP
 *  @input: _unmatchedDirectory - Class:String - \\\\torreypines\\IP\\inbound\\Produnmatched
 *  @output: _eResult - Class:String
 */

import com.sustain.DomainObject;
import com.sustain.cases.model.*;

import com.sustain.expression.Where;
import com.sustain.dir.model.DirLocation;
import com.sustain.lookuplist.model.LookupAttribute;
import com.sustain.lookuplist.model.LookupList;
import com.sustain.person.model.Person
import com.sustain.person.model.PersonProfile;
import com.sustain.properties.model.SystemProperty;
import com.sustain.calendar.model.ScheduledEvent;
import com.sustain.person.model.Identification
import org.apache.commons.io.FilenameUtils;
import com.sustain.cases.model.Sentence;
import com.sustain.cases.model.SentenceMethod;
import com.sustain.entities.custom.Ce_FeeSchedule;;
import com.sustain.entities.custom.Ce_chargeDisposition;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import com.hazelcast.util.StringUtil;
import org.apache.commons.lang.time.DateUtils;

import java.io.FileReader;
import java.io.BufferedReader;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** ------------------------------------------------------------------------------------
 * Interface execution begins here
 */
new IntegrationPublisherInterface(this).exec();

/** ------------------------------------------------------------------------------------
 * Interface Tracking class
 */
class MyInterfaceTracking {
	CtInterfaceTracking tracking_;

	static final String INTERFACE_ = "Interface_OFS_ProcessIntegrationPublisherMessages"
	static final String INTERFACE_TYPE_ = "OFS_INTGPUBL";

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
	static final String TRACKING_DETAIL_CASE_LOG_ = "CASE_LOG";

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
		if( memo != null && !memo.isEmpty() )
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
	// Returns caseNumber, else null if error
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
		if( cCase )
			tracking_.setCase(cCase)
		tracking_.saveOrUpdate();
		return caseNum;
	}

	void setParty(Party cParty) {
		if( cParty )
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
        exception = exception!= null && !exception?.isEmpty() && exception.length() > 255 ? exception.substring(0, 254) : exception;
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

	String setTrackingFile(String sFilename, String sRecIdx="") {
		tracking_.setFilename(sFilename);
		if( !StringUtil.isNullOrEmpty(sRecIdx) )
			tracking_.setFileRecOffset(sRecIdx);
		tracking_.saveOrUpdate();
		return sFilename;
	}

}

/** ------------------------------------------------------------------------------------
 * Odyssey integration publisher interface class
 */
public class IntegrationPublisherInterface {
	// Attributes
	public Boolean bDebug_ = true; 			// debug flag used for script trace reporting
	public Script cRule_;                   // pointer to business rule for logger output
	public List aErrorList_ = [];      		// validation error list
	public Date dExecDate_ = new Date();    // get current date for tracking
    public File cSmbFileShareObj_;

	// Interface defines
	static final String XML_DEFENDANT_PARTY_TYPE_= "DEFT";
	static final String XML_PLAINTIFF_PARTY_TYPE_= "PLA";

	// Odyssey underlying (UCN) caseTypes
	Map mOdyUnderlyingCaseTypes_ = [
		'PRCS' 		: 'R',
		'PAROLE'  	: 'P'
	];

	// System Property attribute class
	class MySysProperties {
		String sSmbFileUsername_;
		String sSmbFilePassword_;
		String sEmailList_;
		String seSuiteEnvURL_;
	}
	public MySysProperties cSysProps_ = null;

	// Entity attributes
	MyInterfaceTracking iTracking_;        // pointer to tracking interface

	// For debug purposes
	public StringBuilder sLoggerOutputBuf_ = new StringBuilder();
	public StringBuilder sCaseLoggerOutputBuf_ = new StringBuilder();

	/** ------------------------------------------------------------------------------------
	 * Constructor
	 * @param rule = pointer to current business rule for logger output
	 */
	IntegrationPublisherInterface(Script rule) {
		this.cRule_ = rule
		this.cSysProps_= new MySysProperties();
	}

	/** ------------------------------------------------------------------------------------
	 * Main interface execution handler
	 */
	public void exec() {
      cSmbFileShareObj_ = new File (cRule_._eslInboundQueueSmbPath);
		try {
			// Initialize tracking system
			logger("Script execution started @ ${dExecDate_}");
			iTracking_ = new MyInterfaceTracking(dExecDate_);

			// Get system assignments
			if ( assignSystemProperties() ) { // valid?

				// Connect SMB network file share object and set credentials
				//cSmbFileShareObj_ = new SmbFileWrapper(cSysProps_.sSmbFileUsername_,cSysProps_.sSmbFilePassword_);

				// Load/Process all IP xml files in queue
				LoadAndProcessIPFiles();
			}

			// Finalize script execution
			logger("Script complete");
			finalizeScriptExecution();

			// Create return json response
			cRule_._eResult= iTracking_.tracking_.result;

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::exec - Case execution handler");
			iTracking_.updateResult(MyInterfaceTracking.RESULT_FAIL_EXCEPTION_);
		}

	}

	/** -------------------------------------------------------------------------------------------------------
	 * Assign all System Properties
	 * @returns (0= Failure, 1= Successful)
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

	/** -------------------------------------------------------------------------------------------------------
	 * Load/Process all integration publisher xml files
	 * @returns (0= Failure, 1= Successful)
	 */
	public boolean LoadAndProcessIPFiles() {

		// Open input path and load/sort file list
		ArrayList fFiles = new ArrayList();
		try {
			// Open network share folder for processing inbound IP files
          /*if( !cSmbFileShareObj_ ){ // connect URL and test connection to network, invalid?
				throw new Exception( "Error connecting to ${cRule_._eslInboundQueueSmbPath} network URL, check server/user/pwd credentials");
          }*/
			logger "Searching ${cRule_._eslInboundQueueSmbPath} network share for Integration Publisher files";

			// Filter out and sort all files for processing
			//fFiles = cSmbFileShareObj_.cSmbFile_.listFiles().findAll {
          
          cSmbFileShareObj_.listFiles().each({file ->
             if (!file.getName().contains("_append_")){
      String fileName = file.getPath();
      String timeLongValue = new Date().getTime();
      file.renameTo(fileName.replace(".xml", "_append_${timeLongValue}.xml".toString()));
             }});
          
            fFiles = cSmbFileShareObj_.listFiles().findAll {  
			         it.toString().toUpperCase() =~ ".XML"}.toList().sort { it.name };
			logger(fFiles?.size() + " file(s) found to process");

			if (fFiles) {   // input files?
				// Process all inbound integration xml files
				for (file in fFiles) {

					if ( !LoadParseIPFile(file) ) // load/parse IP file, exception?
						return false;
				}
			}
			return true;

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::LoadIntegrationPublisherFiles - load file error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
	}

	/** ------------------------------------------------------------------------------------
	 * Load integration publisher xml file and parse xml sluper
	 * @param fInputFile = IP xml file
	 * @Return 0=Failed, 1=Success
	 */
	public boolean LoadParseIPFile(File fInputFile) {

		boolean bRetVal= false;

		// Clear tracking parameters for next case
		aErrorList_.clear(); // clear for new execution
		this.sCaseLoggerOutputBuf_.setLength(0);
		java.util.stream.Stream line;
        ArrayList array;
		try {

			// Create a case interface tracking instance for each case line record
			MyInterfaceTracking cTracking = new MyInterfaceTracking(new Date());
			cTracking.setTrackingFile(fInputFile.getName());

			// Read/Parse IP file from network share
			logger "Read/Parse IP ${fInputFile.getPath()} file from network share";
			String xmlFileContents = "";
            FileReader fr = new FileReader(fInputFile);
			BufferedReader bf = new BufferedReader(fr);
			line = bf.lines();
			array = line.toArray();
			for (i in array){
  				xmlFileContents += i;
			}
            String sXmlData = xmlFileContents;
            bf.close();
			Object xmlIPSluper = new XmlSlurper().parseText(sXmlData);
          if (cRule_._excludeCaseTypes.contains(xmlIPSluper.Case.CaseType.@Word)){
  				org.apache.commons.io.FileUtils.moveFileToDirectory( fInputFile, new File(cRule_._excludeDirectory), false);
                logger("Case.CaseType == ${xmlIPSluper.Case.CaseType.@Word}; this file has been moved to the exclude directory.");
				return true;
            }
          
            logger "validate ip file start"
			if( xmlIPSluper ) {	 // valid?
				ValidateIPFile( cTracking, xmlIPSluper, fInputFile );
			} else {
				this.aErrorList_.add(new ValidationError(false, "", fInputFile.getName(), logger("Error parsing ${fInputFile.getName()} IP xml file") ));
			}
			logger "validate ip file end"
			// Process IP xml file errors
			if( (bRetVal = processCaseErrors( cTracking, fInputFile )) ) {    // no exceptions?

				// Finalize case interfaceTrackingDetail and batch error reporting
				bRetVal = finalizeCaseTrackingDetail( cTracking, fInputFile );
			}

			// Add completion case tracking entry
			cTracking.addTrackingDetail(iTracking_.TRACKING_DETAIL_CASE_LOG_, iTracking_.TRACKING_STATUS_END_,"${iTracking_.INTERFACE_} Log", logger("Case results = ${cTracking.tracking_.result}"), sCaseLoggerOutputBuf_.toString());

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::LoadParseIPFile - ${fInputFile.getName()} file read error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
		return bRetVal;
	}

	/** ------------------------------------------------------------------------------------------------------------
	 * Validate integration publisher xml elements
	 * @param cTracking - error tracking pointer
	 * @param xmlFilePath - parse xml sluper
	 * @param fFilePath - IP xml file
	 * @returns (0= Failure, 1= Successful)
	 */
	public boolean ValidateIPFile( MyInterfaceTracking cTracking, Object xmlIPSluper, File fFilePath ) {
		boolean bRetVal= false; 	// guilty until proven innocent
		Case cCase= null;
		Timestamp now = Timestamp.valueOf(LocalDateTime.now())

		try {
			logger("Validating Integration Publisher file ${fFilePath.getName()}");
          logger("cCase ${cCase}")
			// Find an ePros case (Validation errors are reported via getCase method)
          if (!(cCase=getCase( xmlIPSluper, fFilePath ))){
            return bRetVal;
          }
				
			cTracking.setCase(cCase);
          if (cCase != null && !cCase.collect("specialStatuses[(status == 'LOCKED' && endDate == null) || (status == 'LOCKED' && endDate != null && endDate.after(#p1))]", now).isEmpty()){

            return bRetVal;
          }
			// Process all IP xml elements
			cRule_.withTx {	// env. tx block
              
				bRetVal = ProcessIPFile(cTracking, cCase, xmlIPSluper, fFilePath);

			}

        }catch (Exception ex) {
          if (ex.getMessage().contains("Row was updated or deleted by another transaction")){
            logger("Row was updated or deleted by another transaction")
          }else{
			logger iTracking_.setException(ex.getMessage(), "Exception::ValidateIPFile - Error validating ${fFilePath.getName()} IP file");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);

          }
		}

		return bRetVal;
	}

	/** ------------------------------------------------------------------------------------------------------------
	 * Process Integration Publisher xml file to update case charge and sentencing information.
	 * @param cTracking - error tracking pointer
	 * @param xmlFilePath - parse xml sluper
	 * @param fFilePath - IP xml file
	 * @returns (0= Failure, 1= Successful)
	 */
	public boolean ProcessIPFile( MyInterfaceTracking cTracking, Case cCase, Object xmlIPSluper, File fFilePath ) {
		boolean bRetVal= false;

        List<ChargeStatuteObj> lcChgDispStatuteList = []; // list use to track statute information for dispositionEvents
		String sGenericStatuteSectionNbr = "9999";
		CasePartyObj cDefPtyObj= null;
		String ipCaseTitle = xmlIPSluper.Case.CaseTitle.text();
        Party ipDefendant = cCase.collect("parties[partyType == 'DEF']").find({it -> it.person.lastName != null && ipCaseTitle.contains(it.person.lastName)});
        try {
			logger("Processing IP ${fFilePath.getName()} - CaseTitle(${xmlIPSluper.Case.CaseTitle})");

			// Attempt to match the IP xml DEFT party w/ a DEF party in ePros
			logger("Searching for defendant party");
			Party cDefParty = null;
			String sCaseDefPartyId = null;
			cDefPtyObj = getCaseParty(cCase, xmlIPSluper, XML_DEFENDANT_PARTY_TYPE_);	// search for DEFT party match
			if( !cDefPtyObj.isValid() ) { 	// party + DEFT object valid?
				this.aErrorList_.add(new ValidationError(true, cCase.caseNumber, fFilePath.getName(), logger("No subCase DEF party match, check CaseParty [DEFT] InternalNameID")));
				return false;
			}
			cDefParty = cDefPtyObj.cParty_;
            cDefParty = ipDefendant != null && ipDefendant.subCase != null ? ipDefendant : cDefParty;
			sCaseDefPartyId= cDefPtyObj.getCasePartyId();
			cTracking.setParty(cDefParty);
			SubCase cSubCase = cDefParty.subCase; // get subCase associated w/ party
			cSubCase.filingDate = convDateStrToDate(xmlIPSluper.Case.FiledDate.text(), "MM/dd/yyyy");
			// Load case specific fields
			//cCase.originalFiledDate = convDateStrToDate(xmlIPSluper.Case.FiledDate.text(), "MM/dd/yyyy");
			//cCase.caseType = xmlIPSluper.Case.CaseType.@Word;  // use attribute
          String ipCaseTypeWord = xmlIPSluper.Case.CaseType.@Word;
          for (number in xmlIPSluper.Case.CaseNumber.text().trim().split("[;,]")){
          if (ipCaseTypeWord == "P" && cCase.collect("otherCaseNumbers[type == #p1 && number == #p2]", "PAROLE", number).isEmpty()){
            OtherCaseNumber otherCaseNumber = new OtherCaseNumber();
            otherCaseNumber.number = number;
            otherCaseNumber.type = "PAROLE";
            cCase.add(otherCaseNumber, "otherCaseNumbers");
            cCase.saveOrUpdate();
            cSubCase.add(otherCaseNumber, "otherCaseNumbers");
            cSubCase.saveOrUpdate();
          }
          if (ipCaseTypeWord == "R" && cCase.collect("otherCaseNumbers[type == #p1 && number == #p2]", "PRCS", number).isEmpty()){
            OtherCaseNumber otherCaseNumber = new OtherCaseNumber();
            otherCaseNumber.number = number;
            otherCaseNumber.type = "PRCS";
            cCase.add(otherCaseNumber, "otherCaseNumbers");
            cCase.saveOrUpdate();
            cSubCase.add(otherCaseNumber, "otherCaseNumbers");
            cSubCase.saveOrUpdate();
          }
            if(cCase.collect("otherCaseNumbers[type == 'CRT' && number == #p1]", number).isEmpty() && cCase.collect("otherCaseNumbers[type == 'PRCS' && number == #p1]", number).isEmpty() && cCase.collect("otherCaseNumbers[type == 'PAROLE' && number == #p1]", number).isEmpty()){
            OtherCaseNumber otherCaseNumber = new OtherCaseNumber();
            otherCaseNumber.number = number;
            otherCaseNumber.type = "CRT";
            cCase.add(otherCaseNumber, "otherCaseNumbers");
            cCase.saveOrUpdate();
            cSubCase.add(otherCaseNumber, "otherCaseNumbers");
            cSubCase.saveOrUpdate();
            }
         }
			// ----------------- Process all case flags/statuses

			logger("<b>Adding case flags</b>");
			for (int cf = 0; cf < xmlIPSluper.Case.CaseFlag.size(); cf++) {
				String sCaseFlag = xmlIPSluper.Case.CaseFlag[cf].@Word;     // get caseFlag attribute code

				logger("Processing caseFlag($sCaseFlag)");
				List<PartySpecialStatus> lPtySpcStatFlgs = cDefParty.collect("specialStatuses[status == 'CFLAG' and cf_partyFlag == #p1]",sCaseFlag);
				PartySpecialStatus cPtySpcStatFlg = lPtySpcStatFlgs.last() ?: new PartySpecialStatus();
				logger( ((lPtySpcStatFlgs.empty) ? "Adding new" : "Updating existing") + " PartySpecialStatus flags" );
				cPtySpcStatFlg.status = "CFLAG";
				cPtySpcStatFlg.cf_partyFlag = sCaseFlag;
				cPtySpcStatFlg.party = cDefParty;

				if (lPtySpcStatFlgs.empty)   // new entry?
					cDefParty.add(cPtySpcStatFlg);
			}

			// ----------------- Process case judge
			logger "498"
			if (xmlIPSluper.Case.CaseJudge.size() ) {    // judge available?
				logger("Searching for exisiting caseJudge - ${xmlIPSluper.Case.CaseJudge.text()}");
				Ce_Participant cCaseParticipant = cCase.collect("ce_Participants[type=='COURT' and subType=='JUD']").find{ p ->
   												   !p.person.collect("identifications[identificationType=='ODSYJUDGE' and identificationNumber==#p1]",(String)xmlIPSluper.Case.CaseJudge.@Word).empty
				};
                  Person cPerson= null;
                  cPerson=getPerson((String)xmlIPSluper.Case.CaseJudge.text(),'COURT','JUD','ODSYJUDGE',(String)xmlIPSluper.Case.CaseJudge.@Word);
                  cCaseParticipant = cCaseParticipant == null ? cCase.collect("ce_Participants[type=='COURT' && subType=='JUD' && person == #p1]", cPerson) : cCaseParticipant;
				if ( cCaseParticipant == null ) { // no participant?
					logger("Judge - ${xmlIPSluper.Case.CaseJudge.text()} not found, adding participant");
					Ce_Participant cJudParticipant = new Ce_Participant();
					cJudParticipant.type = 'COURT';
					cJudParticipant.subType = 'JUD';
					cJudParticipant.status = 'ACT';
					cJudParticipant.statusDate = new Date();

					// Find/Create associated person based on identification type/code
					//Person cPerson= null;
					//if( (cPerson=getPerson((String)xmlIPSluper.Case.CaseJudge.text(),'COURT','JUD','ODSYJUDGE',(String)xmlIPSluper.Case.CaseJudge.@Word)) != null ) {
                  if (cPerson != null){
						// Update participant entity w/ person reference
						cJudParticipant.case = cCase;
						cJudParticipant.person = cPerson;
						cJudParticipant.saveOrUpdate();
						cCase.ce_Participants.add(cJudParticipant);

					} else
						this.aErrorList_.add(new ValidationError(false, cSubCase.case.caseNumber, fFilePath.getName(), logger("Error finding '$sIdentType' person type w/ idNbr($sAssignCode)")));

				} else
					logger("Found participant Judge - ${xmlIPSluper.Case.CaseJudge.text()}");
			}

			// ----------------- Add party/person information

			// Process defendant party section
			Object oDefPtyNode = cDefPtyObj.oCasePartyObj_;
			if (oDefPtyNode != null) {

				// Add DOB if required
				logger( "<b>Validating DOB</b>" );
				if (!StringUtil.isNullOrEmpty((String) oDefPtyNode.DateOfBirth.text())) {
					Date dDob = convDateStrToDate((String) oDefPtyNode.DateOfBirth.text(), "MM/dd/yyyy")
					List<PersonProfile> lPersonProf = cDefParty.collect("person.profiles[dateOfBirth == null or dateOfBirth == #p1]", dDob);
					logger(((lPersonProf.empty) ? "Adding new" : "Updating existing") + " DOB personProfile - $dDob");

					// Add/update profile
					PersonProfile cPersonProf = lPersonProf.last() ?: new PersonProfile(); // if null, create new object
					cPersonProf.dateOfBirth = dDob;
					cPersonProf.setAssociatedPerson(cDefParty.person);

					if (lPersonProf.empty) // new?
						cDefParty.person.profiles.add(cPersonProf)
				}

				// Add/Update any valid identification types
				logger( "<b>Validating SSN/CDL#'s</b>" );
				["SSN", "CDL"].each { id ->
					List<Identification> lId = cDefParty.collect("person.identifications[identificationType==#p1]", id);
					logger(((lId.empty) ? "Adding new" : "Updating existing") + " $id Identification");
					Identification cId = lId.last() ?: new Identification();   // if null, create new object otherwise use existing instance
					cId.identificationType = id;

					// Process all identification types
					switch (id) {
						case 'SSN': // Social Security#
							if (!StringUtil.isNullOrEmpty((String)oDefPtyNode.SocialSecurityNumber.text()) && oDefPtyNode.SocialSecurityNumber.text() != '000-00-0000' ) {
								cId.identificationNumber = oDefPtyNode.SocialSecurityNumber.text();
								logger("SSN = " + cId.identificationNumber );
							}
							break;

						case 'CDL': // Drivers License#
							if (!StringUtil.isNullOrEmpty((String)oDefPtyNode.DriversLicense.DriversLicenseNumber.text())) {
								cId.identificationNumber = oDefPtyNode.DriversLicense.DriversLicenseNumber.text();
								logger("CDL = " + cId.identificationNumber );
								if (!StringUtil.isNullOrEmpty((String)oDefPtyNode.DriversLicense.DriversLicenseState.@Word)) {
									cId.issuerState = oDefPtyNode.DriversLicense.DriversLicenseState.@Word;
									logger("CDL issuer state = " + cId.issuerState );

									// Validate the issuer state w/ lookup list
									if( validateLookupCode(cId.issuerState, "US_STATE") == null )
										this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("CDL issuer state ${cId.issuerState} not found in LookupList[US_STATE]")));
								}
							}
							break;
					}

					// Assign identification number and create new instance if required
					if (lId.empty && cId.identificationNumber) { // new w/ valid identification
						cId.associatedPerson = cDefParty.person;
						cDefParty.person.identifications.add(cId);
					}
				}
			}

			// ----------------- Add defendant charges lines 659-892

			logger("655:<b>Adding defendant charges</b>");
			int iChgIdx= 0;
          if (cCase.collect("specialStatuses[endDate == null && status == 'LOCKEDCHARGES']").isEmpty()){
          for( oChgObj in xmlIPSluper.Case.Charge.findAll({c -> c.@PartyID == sCaseDefPartyId && !c.ChargeHistory.findAll({itH -> itH.@Stage == "Case Filing" && itH.Statute.Degree.@Word != "NULL"}).isEmpty()})){
            
              logger("TEST:662:oChgObj:${oChgObj}")
              	String ipCaseType = xmlIPSluper.Case.CaseType.@Word.toString();
              	def ipChargeHistoryCollection = oChgObj.ChargeHistory.findAll({chistory -> chistory.@CurrentCharge == "true" && (chistory.@Stage == "Case Filing" || chistory.@Stage != null)});
                String sChgDegree = ipChargeHistoryCollection[ipChargeHistoryCollection.size() -1].Statute.Degree?.text()?.toUpperCase();
                String sChgDegreeWord = ipChargeHistoryCollection[ipChargeHistoryCollection.size() -1].Statute.Degree.@Word.toString();
                String sChgSectionNumber = ipChargeHistoryCollection[ipChargeHistoryCollection.size() -1].Statute.StatuteCode.@Word.toString();
				String sChgNbr = oChgObj?.ChargeTrackNumber?.text()?.replaceAll("^0*", ""); // Add charge# and remove leading 0's
                String sChgStatuteDescription = ipChargeHistoryCollection[ipChargeHistoryCollection.size() -1].Statute.StatuteDescription?.text()?.toUpperCase();

				// Test for valid charge number
				if ( !StringUtil.isNullOrEmpty(sChgNbr) ) {   // valid IP charge#
				  logger("666:Searching for exisiting charge#($sChgNbr)");
                  logger("667:party:${cDefParty}; cf_ofsWord:${sChgSectionNumber}; category:${sChgDegree}; sChgNbr:${sChgNbr}")

                  //DomainObject.find(Ce_StatuteLanguageChoice.class, "choice", sStatuteNumber, "odysseyDegree", degreeCode);
                  boolean disamendStatus = false;
                  
                  String degreeCodeMatch = DomainObject.find(LookupItem.class, "lookupList.name", "ODY_DEGREE").find({thisItem -> thisItem.label.toLowerCase() == sChgDegree.toLowerCase() || thisItem.code == sChgDegree})?.code;
                  
                  ArrayList inputStatuteChoiceList = DomainObject.find(Ce_StatuteLanguageChoice.class, "choice", sChgSectionNumber, "odysseyDegree", sChgDegreeWord, "agency", "ODY").statute;
                  
                  inputStatuteChoiceList = inputStatuteChoiceList?.isEmpty() ? DomainObject.find(Ce_StatuteLanguageChoice.class, "choice", sChgSectionNumber, "odysseyDegree", degreeCodeMatch, "agency", "ODY"): inputStatuteChoiceList;
                  
                  ArrayList inputStatuteChoiceListInbound = DomainObject.find(Ce_StatuteLanguageChoice.class, "choice", sChgSectionNumber, "odysseyDegree", sChgDegreeWord, "agency", "ODY", "choiceType", "INBOUND").statute;
                  
                  List<Charge> lChg = cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '') == #p1 && statute != null && #p2.contains(statute)]", sChgNbr, inputStatuteChoiceList);
                  
                  lChg = lChg?.isEmpty() ? cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '') == #p1 && statute != null && #p2.contains(statute)]", sChgNbr, inputStatuteChoiceListInbound) : lChg;
                  
                  List<Charge> existinglChg = cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '') == #p1 && statute != null]", sChgNbr).findAll({charge -> Condition.get("Charge is Active and Type is not enhancement or violation").isTrue(charge)});
                  
                  Statute inputStatute = inputStatuteChoiceList.find({statute -> lChg.statute.contains(statute) || statute.id != null});
                  
                  lChg = lChg?.isEmpty() ? cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '') == #p1 && statute != null && statute.sectionNumber == #p2]", sChgNbr, sGenericStatuteSectionNbr) : lChg;
                  
                  Charge cCharge = !lChg?.isEmpty() ? lChg.find({charge -> Condition.get("Charge is Active and Type is not enhancement or violation").isTrue(charge) || charge.id != null}) : new Charge();
                  
                  logger("TEST:697:choice:${sChgSectionNumber}; odysseyDegree:${sChgDegreeWord}; ");
                  logger("TEST:698:lChg:${lChg}; inputStatuteChoiceList:${inputStatuteChoiceList}; sChgDegreeWord:${sChgDegreeWord}; sChgNbr:${sChgNbr}");
                  logger("TEST:700:inputStatuteChoiceListInbound:${inputStatuteChoiceListInbound}");
                  if (cCharge == null || cCharge.id == null){
                    if (!inputStatuteChoiceList?.isEmpty() || !inputStatuteChoiceListInbound.isEmpty()){
                      logger("TEST:707:set statute, ${cCharge.statute}, for new charge, set DISAMEND status for current charges");
                      cCharge.statute = inputStatuteChoiceList.find({it -> it.id != null});
                      cCharge.statute = cCharge.statute == null ? inputStatuteChoiceListInbound.find({it -> it.id != null}) : cCharge.statute;
                    } else {
                      cCharge.statute = DomainObject.find(Statute.class, 'sectionNumber', sGenericStatuteSectionNbr).find({thisStatute -> thisStatute.id != null});
                      cCharge.description = "${sChgDegree} ${sChgSectionNumber}".toString();
                    }
                    logger("TEST:710")
                    if ((!existinglChg?.findAll({it -> it.chargeNumber == sChgNbr || it.chargeNumber == "0${sChgNbr}".toString()}).isEmpty() || cCharge.statute.sectionNumber  == sGenericStatuteSectionNbr) && !["P","R"].contains(xmlIPSluper.Case.CaseType.@Word.toString())){
                      logger("TEST:712")
                      disamendStatus = true;
                      existinglChg.each({thisCharge ->
                      List<Ce_chargeDisposition> disamends = DomainObject.find(Ce_chargeDisposition.class, "charge", thisCharge, "dispositionType", "DISAMEND");
                        if (disamends.isEmpty()){
                          Ce_chargeDisposition newChargeDisposition = new Ce_chargeDisposition();
                          newChargeDisposition.dispositionType = "DISAMEND";
                          newChargeDisposition.charge = thisCharge;
                          thisCharge.add(newChargeDisposition, "ce_chargeDispositions");
                          thisCharge.dispositionType = "DISAMEND";
                          thisCharge.saveOrUpdate();
                        }
                      });
                    }
                  }
                  
                  Timestamp today = Timestamp.valueOf(LocalDateTime.now());
                  
                  if (cCharge.status == "ACT" || cCharge.id == null)
					if (cCharge.dispositionType != "DISAMEND"){
					logger("678");
                    //cCharge.description = "${sChgDegree} ${sChgSectionNumber}".toString();
					cCharge.chargeDate = convDateStrToDate((String) oChgObj?.ChargeOffenseDate?.text(), "MM/dd/yyyy");
					cCharge.chargeNumber = sChgNbr.replaceAll('^0*', '');
					cCharge.status = "ACT";
					cCharge.statusDate = new Date();
					cCharge.stageAdded = "FILED";
                    cCharge.setAssociatedParty(cDefParty);
                    
                    cCharge.status = cCharge.status != "LOCKED" && oChgObj.ChargeHistory.find({chistory -> !["P","R"].contains(ipCaseType) && chistory.@Stage == "Case Filing" || ["P","R"].contains(ipCaseType) && chistory.@Stage != null}).AmendedDate != null && !oChgObj.ChargeHistory.find({chistory -> !["P","R"].contains(ipCaseType) && chistory.@Stage == "Case Filing" || ["P","R"].contains(ipCaseType) && chistory.@Stage != null}).AmendedDate?.isEmpty() ? "AMEND" : cCharge.status;
                      
                    cCharge.statusDate = oChgObj.ChargeHistory.find({chistory -> !["P","R"].contains(ipCaseType) && chistory.@Stage == "Case Filing" || ["P","R"].contains(ipCaseType) && chistory.@Stage != null}).AmendedDate != null && !oChgObj.ChargeHistory.find({chistory -> !["P","R"].contains(ipCaseType) && chistory.@Stage == "Case Filing" || ["P","R"].contains(ipCaseType) && chistory.@Stage != null}).AmendedDate?.isEmpty() ? Date.parse('MM/dd/yyyy', oChgObj.ChargeHistory.find({chistory -> !["P","R"].contains(ipCaseType) && chistory.@Stage == "Case Filing" || ["P","R"].contains(ipCaseType) && chistory.@Stage != null}).AmendedDate.text()) : cCharge.statusDate;
                      
                    cCharge.saveOrUpdate();
					// Check for underlying caseTypes and add UCN ChargeType if needed
					String sUCNCaseType = (String) mOdyUnderlyingCaseTypes_.find { it.value == (String)xmlIPSluper.Case.CaseType.@Word; }?.key;
					if( !StringUtil.isNullOrEmpty(sUCNCaseType) ) { // valid?
						if (validateLookupCode(sUCNCaseType, "CHARGE_TYPE") == null) // Test chargeType against lookupList, invalid?
							this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("721:UCN chargeType($sUCNCaseType) not found in LookupList[CHARGE_TYPE]")));
						logger( "722:Adding UCN ChargeType($sUCNCaseType) to charge" );
						//cCharge.chargeType = sUCNCaseType;
					}
                    // Add charge statute information using the most current ChargeHistory section based on charge#
					logger("726:Searching charge history for the latest charge - chargeNbr(${cCharge.chargeNumber})");
					List<Object> lChgHistList = xmlIPSluper.Case.Charge.ChargeHistory.findAll { v -> ( !["P","R"].contains(ipCaseType) && v.@Stage == "Case Filing" || ["P","R"].contains(ipCaseType) && v.@Stage != null) && v.ChargeNumber.text().replaceAll("^0*","") == cCharge?.chargeNumber?.replaceAll("^0*","") }.collect{it}; // get list of charges to sort
                    
                      
                    def ipChargeHistoryList = oChgObj.ChargeHistory.findAll({chistory -> chistory.@Stage == "Case Filing" || chistory.@Stage != null});

                    Object oChargeHistory = !ipChargeHistoryList.list().isEmpty() ? ipChargeHistoryList.list().last(): null; logger("735");
                    if( oChargeHistory != null ) { // valid?
						logger("737:Out of ${lChgHistList.size()} charge(s), found latest charge history ${oChargeHistory.TimestampCreate.text()}");
						String sStatuteCode = oChargeHistory.Statute.StatuteNumber?.text()?.toUpperCase(); // eg. PC
						String sStatuteNumber = oChargeHistory.Statute.StatuteCode?.@Word; // eg. 422
						String sStatuteDesc = oChargeHistory.Statute.StatuteDescription?.text(); // eg. Criminal Threats
						String sStatuteDegree = oChargeHistory.Statute.Degree?.text()?.toUpperCase();
						/**
						 * Compare charge statute w/ xml statute, if not the same add to dispo reporting memo.
						 * If statute in XML is for an existing charge in eProsecutor and the Statute is different
						 * (based on Charge.chargeNumber and Statute.sectionCode in ePros) add the StatuteCode Word
						 * and StatuteDescription using the format: Word & "-" & StatuteDescription to the .memo field
						 * within the Ce_chargeDisposition entity that gets added elsewhere in this mapping.
						 */
						boolean bForceStatuteUpdate = true; logger("749");
						/*if (!lChg.empty && cCharge?.statute != null) {    // updating existing charge w/ valid statute?
							logger("751:Comparing publishedStatute($sStatuteNumber|$sStatuteDesc) to existing charge statute");
							if ( cCharge.statute.cf_ofsWord != sStatuteNumber ) { // invalid?
								String sStatuteMemo = "${sStatuteNumber}-${sStatuteDesc}";  // create dispo memo
								this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("PublishedStatute($sStatuteMemo) != eProsStatute(${cCharge.statute.sectionNumber}-${cCharge.statute.sectionName})] for existing charge#(${cCharge.chargeNumber})")));
								logger("755:Adding new statute to chargeDispo memo - ($sStatuteMemo)");

								// Add memo to statute tracking list to be used w/ the dispo sentencing
								lcChgDispStatuteList.add(new ChargeStatuteObj(cCharge.chargeNumber, sStatuteMemo));
                              bForceStatuteUpdate = false; logger("756: bForceStatuteUpdate ${bForceStatuteUpdate}"); // don't allow statute update on miscompares
							} else
								logger("761: Found matching statute");
						}*/
						/**
						 * Create/Update statute on charge
						 * If statute is for a new charge not in eProsecutor (based on chargeNumber) match statute on
						 * source, then sectionCode. If no statute can be found use the generic Statute sectionNumber
						 * 9999 and add the StatuteCode Word and StatuteDescription using the format: Word & "-" &
						 * StatuteDescription to the .memo field within the Ce_chargeDisposition entity that gets
						 * added elsewhere in this mapping.
						 */
                      logger("789: choice:${sChgSectionNumber}; odysseyDegree:${degreeCodeMatch}; inputStatuteChoiceList:${inputStatuteChoiceList}");
                      logger("790:inputStatute:${inputStatute};cCharge.statute${cCharge?.statute}");
                      if (inputStatute != cCharge.statute){
							// Statute search criteria - ('=source',"PC",'=sectionNumber',"422",'=sectionName',"Criminal Threats")
							//logger("Searching for published charge statute - [Src(${sStatuteCode}), Nbr(${sStatuteNumber}), Desc(${sStatuteDesc}), Category(${sStatuteDegree.charAt(0)})]");
							//Where w = new Where();
							//w.addEquals('source', sStatuteCode); // eg. PC
							//w.addEquals('category', sStatuteDegree.charAt(0)); // eg. 422
                            //w.addEquals('cf_ofsWord', sStatuteNumber);
                            //Statute cSt = (Statute) DomainObject.find(Statute.class, "cf_ofsDescription", "like", "%${sStatuteDesc}%", "category", "like", "${sStatuteDegree.charAt(0)}", w)[0];
                            //Statute cSt = (Statute) DomainObject.find(Statute.class, "category", "like", "${sStatuteDegree.charAt(0)}", w)[0];
                          	//Statute cSt = DomainObject.find(Statute.class, w).find({it -> it.category.toString().startsWith(sStatuteDegree[0])});
                          String degreeCode = DomainObject.find(LookupItem.class, "lookupList.name", "ODY_DEGREE").find({thisItem -> thisItem.label.toLowerCase() == sStatuteDegree.toLowerCase() || thisItem.code == sStatuteDegree})?.code; logger("782: ${DomainObject.find(LookupItem.class, "lookupList.name", "ODY_DEGREE").find({thisItem -> thisItem.label.toLowerCase() == sStatuteDegree.toLowerCase() || thisItem.code == sStatuteDegree})}");
                            ArrayList statuteChoiceList = DomainObject.find(Ce_StatuteLanguageChoice.class, "choice", sStatuteNumber, "odysseyDegree", degreeCode, "choiceType", "INBOUND");
                          Statute cSt = statuteChoiceList.find({choice -> choice != null && choice.statute != null})?.statute;
                        
                        logger("second statute match : statuteChoiceList");

                          logger("785 cSt: ${cSt}; choice: ${sStatuteNumber}; odysseyDegree:${sStatuteDegree} ${degreeCode}");
							if (cSt == null || cSt.sectionNumber == sGenericStatuteSectionNbr) { // no statute?
								logger("Statute not found, searching for generic sectionNbr($sGenericStatuteSectionNbr)");

								// If failed to find xml statute, check for a Generic statute sectionNumber and use that
								cSt = (Statute)DomainObject.find(Statute.class, '=sectionNumber', sGenericStatuteSectionNbr)[0];
								if (cSt != null) { // valid generic statute?

									// Add information to statute tracking list for use w/ dispo event
									String sStatuteMemo = "${sStatuteNumber}-${sStatuteDesc}";  // dispo memo
									this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("PublishedStatute($sStatuteMemo) not found, adding default charge.statute.sectionNumber($sGenericStatuteSectionNbr)")));

									// Add memo to statute tracking list to be used w/ the dispo sentencing
                                  
									lcChgDispStatuteList.add(new ChargeStatuteObj(cCharge.chargeNumber, sStatuteMemo));
                                    cCharge.description = "${sChgDegree} ${sChgSectionNumber}".toString();
                                  cCharge.memo = "${sChgStatuteDescription}".toString();
                          			cCharge.saveOrUpdate();
								}

                            } else{
                              logger("Found statute, adding to charge ; cCharge.statute: ${cCharge} ${cCharge.id} ${cCharge.statute} cSt:${cSt}");
                            }
							// Assign statute to charge if valid otherwise skip charge insert
							if ( cSt != null && cCharge.statute == null ) {  // valid?
                              	logger("799 cSt is valid")
                                 if ( inputStatuteChoiceList.find({choice -> choice != null && choice.statute != null}).odysseyAttributes != null ){
                                   if ( !cCharge.chargeAttributes.contains("ATTEMPT") ){
                                     cCharge.chargeAttributes = ["ATTEMPT"]
                                   }
                                   if ( !cCharge.chargeAttributes.contains("CONSP") ){
                                     cCharge.chargeAttributes = ["CONSP"]
                                   }
                                   if ( !cCharge.chargeAttributes.contains("SOL") ){
                                     cCharge.chargeAttributes = ["SOL"]
                                   }
                                  }
								cCharge.statute = cSt;
                                cDefParty.add(cCharge, "charges");
							} else if (cSt == null) { // invalid!
								this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("No generic statute.sectionNumber($sGenericStatuteSectionNbr) found for charge#${sChgNbr}")));
								logger("No statute found, skipping charge");
								continue; // skip to next charge
							}

                        } else{
							logger("Skipping charge statute update");
                        }
                      //logger("785 updating charge description cCharge.statute ${cCharge.statute} ${cCharge.statute.sectionNumber} ${sChgSectionNumber} - ${cCharge.statute.category[0]} ${sChgDegree.charAt(0)}")
                      /*if (cCharge != null && cCharge.statute != null){
                        if(cCharge.statute.cf_ofsWord != sChgSectionNumber){

                          cCharge.description = "${sChgDegree.charAt(0)} ${sChgSectionNumber} 830".toString();
                          cCharge.saveOrUpdate();
                        }
                      }*/
					} else if (cCharge.statute == null) {  // invalid?
						this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("No published charge xml history found for chargeNbr(${cCharge.chargeNumber})")));
						continue; // skip to next charge
					}

					// Add/save charge references
					//cCharge.setAssociatedParty(cDefParty);
                  	/*cCharge.chargeType = cCharge.status != "AMEND" || (cCharge.statute != null && oChgObj.ChargeHistory.getAt(0).Statute.Degree.@Word.toString().startsWith(cCharge.statute.category)) ? null : cCharge.chargeType;
                    cCharge.saveOrUpdate();*/ logger("844:");
                      Where whereNewCh = new Where();
                      whereNewCh.addContainsAny("chargeNumber", ["${cCharge.chargeNumber}".toString(), "0${cCharge.chargeNumber}".toString()]);
                      DomainObject.find(Charge.class, "associatedParty", cDefParty, "statute", cCharge.statute).each({cpartyCharge -> logger("1:cDefParty: ${cpartyCharge.associatedParty}; chargeNumber: ${cpartyCharge.chargeNumber}; statute: ${cpartyCharge.statute}; status: ${cpartyCharge.status}")});
                      logger("2:cDefParty: ${cDefParty}; chargeNumber: ${cCharge.chargeNumber}; statute: ${cCharge.statute}, status: ${cCharge.status}");
                    /*if (DomainObject.find(Charge.class, "associatedParty", cDefParty, "statute", cCharge.statute, "status", cCharge.status, whereNewCh).size() > 1){
                      DomainObject.delete(cCharge); logger("846: DomainObject.delete(cCharge)");
                    } else{ // new charge?
						cDefParty.charges.add(cCharge); logger("848: cDefParty.charges.add(cCharge)");
                      }*/
                      cDefParty.charges.add(cCharge);
                  }
				} else
					logger("Published chargeID(${oChgObj?.@'ID'}) has no valid chargeNumber, charge skipped");

			} // charges
          }
            // ----------------- Add charge sentences

			// There will a new sentence added for each CAConfinementComponent / CAProbationComponent / ConditionComponent/Fees.
            // The sentenceType/Date/Memo will be duplicated and used on each sentence insert.
			RichList ipSentenceEventsCollection = new RichList();
			xmlIPSluper.Case.SentenceEvent.each({it ->
              it.breadthFirst().each({n ->
                n.name() != "SentenceEvent"?: ipSentenceEventsCollection.add(n);
              });
            });
			//xmlIPSluper.Case.SentenceEvent.eachWithIndex{ oSentEvent, se ->
          	ipSentenceEventsCollection.eachWithIndex{ oSentEvent, se ->
              logger("SentenceEvent ${oSentEvent.@ID} ${se}")
				if ( !StringUtil.isNullOrEmpty((String)oSentEvent.SentenceType.@Word)
					 && !StringUtil.isNullOrEmpty(oSentEvent.SentenceDate.text())) {

					// Search for sentenceType code
					String sSentEvtType = oSentEvent.SentenceType.@Word;
                    if( validateLookupCode(sSentEvtType, "SENTENCE_TYPE") == null )
                        this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("Case.SentenceEvent[$se].SentenceType(${sSentEvtType}) not found in LookupList[SENTENCE_TYPE]")));

                    logger "<b>Processing sentenceEvent[$se] - Type($sSentEvtType)</b>";

                    // Configure mapping header for sentence inserts
                    Date dSentEvtDate = convDateStrToDate((String) oSentEvent.SentenceDate.text(), "MM/dd/yyyy")
                    Map mChgSentenceHdr = [
                            'type': sSentEvtType,
                            'date': dSentEvtDate,
                    ];

					// Find sentencing charge number and assign charge to sentence component methods
					String sChgNbr= (String)oSentEvent.Sentence.SentenceCharge.ChargeNumber?.text()?.replaceAll("^0*", "");
					logger("Searching for charge#($sChgNbr) to assign sentence events");
                    RichList matchingPartyCharges = cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '')==#p1]", sChgNbr);
					Charge cCharge = matchingPartyCharges != null && !matchingPartyCharges?.isEmpty() ? cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '')==#p1]", sChgNbr).last(): null;
					if (cCharge != null) { // valid charge?
						logger("Found charge#($sChgNbr) for sentencing events");

						// Create CAConfinementComponent sentences
						cDefParty = addCAConfinementComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
                        // Create Converted Disposition Component
                        addConvertedDispositionComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
						// Create CommentComponent sentences
						addCommentComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
                        // Create CAProgramsComponent
                        addProgramComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
						// Create CAProbationComponent sentences
						cDefParty = addCAProbationComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);

						// Create ConditionComponent / Schedule Fee sentence (Should be the last sentence insert)
						cDefParty = addConditionComponentAndScheduledFees(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);

					} else
						logger("No valid sentence event charge#(${sChgNbr}) found");
				}
			}
            //process child SentenceEvent
            /*xmlIPSluper.Case.SentenceEvent.SentenceEvent.eachWithIndex{ oSentEvent, se ->
				if ( !StringUtil.isNullOrEmpty((String)oSentEvent.SentenceType.@Word)
					 && !StringUtil.isNullOrEmpty(oSentEvent.SentenceDate.text())) {

					// Search for sentenceType code
					String sSentEvtType = oSentEvent.SentenceType.@Word;
                    if( validateLookupCode(sSentEvtType, "SENTENCE_TYPE") == null )
                        this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("Case.SentenceEvent[$se].SentenceType(${sSentEvtType}) not found in LookupList[SENTENCE_TYPE]")));

                    logger "<b>Adding sentenceEvent[$se] - Type($sSentEvtType)</b>";

                    // Configure mapping header for sentence inserts
                    Date dSentEvtDate = convDateStrToDate((String) oSentEvent.SentenceDate.text(), "MM/dd/yyyy")
                    Map mChgSentenceHdr = [
                            'type': sSentEvtType,
                            'date': dSentEvtDate,
                    ];

					// Find sentencing charge number and assign charge to sentence component methods
					String sChgNbr= (String)oSentEvent.Sentence.SentenceCharge.ChargeNumber?.text()?.replaceAll("^0*", "");
					logger("Searching for charge#($sChgNbr) to assign sentence events");
                    RichList matchingPartyCharges = cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '')==#p1]", sChgNbr);
					Charge cCharge = matchingPartyCharges != null && !matchingPartyCharges?.isEmpty() ? (Charge) matchingPartyCharges.last(): null;
					if (cCharge != null) { // valid charge?
						logger("Found charge#($sChgNbr) for sentencing events");

						// Create CAConfinementComponent sentences
						cDefParty = addCAConfinementComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
						// Create CommentComponent sentences
						addCommentComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
                        // Create CAProgramsComponent
                        addProgramComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);
						// Create CAProbationComponent sentences
						cDefParty = addCAProbationComponent(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);

						// Create ConditionComponent / Schedule Fee sentence (Should be the last sentence insert)
						cDefParty = addConditionComponentAndScheduledFees(cCharge, cDefParty, oSentEvent, mChgSentenceHdr);

					} else
						logger("No valid sentence event charge#(${sChgNbr}) found");
				}
			}*/
            // ----------------- Add disposition sentences
			logger( "<b>Searching for disposition sentences</b>" );
            for (int cd = 0; cd < xmlIPSluper.Case.DispositionEvent.size(); cd++) {
				logger("Processing Case.Disposition Events - ${cd + 1} of ${xmlIPSluper.Case.DispositionEvent.size()}");

                Object oCaseDispo= xmlIPSluper.Case.DispositionEvent[cd];
               	Date dDispEventDate= convDateStrToDate((String)oCaseDispo.DispositionEventDate.text(), "MM/dd/yyyy");

                // Process all Dispositions
                for (int cdc = 0; cdc < xmlIPSluper.Case.DispositionEvent.Disposition.size(); cdc++) {
                    Object oCaseDispos= oCaseDispo.Disposition[cdc];

                    // Find charge associated w/ dispo
                    Object oChgDispo = xmlIPSluper.Case.Charge.find{ c -> c?.@ID == oCaseDispos?.@ChargeID }
                    if( oChgDispo?.size() ) {
                        String sChgNbr = oChgDispo.ChargeTrackNumber?.text()?.replaceAll("^0*", "");
                        String sDispoType = oCaseDispos.DispositionType.@Word.toString();

                        logger( "Processing Case.DispositionEvent[$cd].Disposition[$cdc] - DispoType(${sDispoType}) on Charge#($sChgNbr)" );

                        // Validate disposition type
                        if( validateLookupCode(sDispoType, "CHARGE_DISPOSITION") == null )
                            this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("Case.DispositionEvent[$cd].Disposition[$cdc].DispositionType(${sDispoType}) not found in [CHARGE_DISPOSITION] lookupList")));

                        // Create/Update charge disposition if valid charge number
                        if( !StringUtil.isNullOrEmpty(sChgNbr) ) {
                            RichList matchingPartyCharges = cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '')==#p1]", sChgNbr);
                            Charge cChg= matchingPartyCharges != null && !matchingPartyCharges?.isEmpty() ? (Charge) matchingPartyCharges.last(): null; // get charge involved
                            if( cChg ) { // valid charge?
                                List<Ce_chargeDisposition> lChgDispo = cChg.collect("ce_chargeDispositions[dispositionType==#p1 and dispositionDate==#p2]", sDispoType, dDispEventDate);
                                Ce_chargeDisposition cChgDispo = lChgDispo.last() ?: new Ce_chargeDisposition();  // if null create new object

                                logger(((lChgDispo.empty) ? "Adding new" : "Updating existing") + " ChargeDispo($sDispoType) on charge#($sChgNbr)");

                                // Add charge disposition fields
                                cChgDispo.dispositionDate = dDispEventDate;
                                cChgDispo.dispositionType = sDispoType;

                                // Add statute information to memo computed from above in charge
                                ChargeStatuteObj cStatuteItem= (ChargeStatuteObj) lcChgDispStatuteList.find{ s -> s.sChargeNumber_ == sChgNbr };
                                if( cStatuteItem ){
                                    logger " Adding Statute memo to dispositionEvent - ${cStatuteItem.sStatuteMemo_}";
                                    cChgDispo.memo= cStatuteItem?.sStatuteMemo_ ?: "";
                                }

                                // Add new charge disposition if needed
                                if (lChgDispo.empty) {
                                    cChgDispo.charge = cChg;
                                    cChg.ce_chargeDispositions.add(cChgDispo);
                                }

								// Queue dispo update
								cChgDispo.saveOrUpdate();

                            } else
                                logger( "No party/charge found for charge#(${sChgNbr}), no charge dispositions applied");
                        }
                    } else
                        logger( "No charge section found for dispositionEvent chargeId(${oCaseDispos?.@ChargeID})" );
                }
            }

            // ----------------- Add charge plea's
			logger( "<b>Searching for all charge pleaEvents</b>" );
			List<Object> lEventPlea = xmlIPSluper.depthFirst().findAll{ it.name() == 'PleaEvent' } // findAll PleaEvent sections
			lEventPlea.eachWithIndex { eventPlea, epi ->
				Date dPleaEventDate = convDateStrToDate(eventPlea.PleaEventDate.text(), "MM/dd/yyyy");
				logger "Processing Case.PleaEvent ${epi+1} of ${lEventPlea.size()} - $dPleaEventDate";

				// Process all Plea's within PleaEvent section
				eventPlea.Plea.eachWithIndex { plea, pi ->
					logger "Processing Case.PleaEvent.Plea ${pi+1} of ${eventPlea.Plea.size()}";

                    // Find associated charge to insert Plea on
                    Object oChgPlea = xmlIPSluper.Case.Charge.find{ c -> c?.@ID == plea?.@ChargeID };
                    if( oChgPlea?.size() ) {
                        String sChgNbr= oChgPlea.ChargeTrackNumber?.text()?.replaceAll("^0*", "");

                        // Validate plea type
                        String sPleaType = validateLookupCode((String)plea.PleaType?.@Word,"PLEA_TYPE");
                        if( StringUtil.isNullOrEmpty(sPleaType) ) // invalid?
                            this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("PleaType(${sPleaType}) not found in [PLEA_TYPE] lookupList")));

                        // Create/Update charge plea if valid charge number
                        if (!StringUtil.isNullOrEmpty(sChgNbr)) {
                            RichList matchingPartyCharges = cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '')==#p1]", sChgNbr);
                            Charge cChg = matchingPartyCharges != null && !matchingPartyCharges?.isEmpty() ?  cDefParty.collect("charges[chargeNumber?.replaceAll('^0*', '')==#p1]", sChgNbr).last(): null;
                            // get charge involved
                            if (cChg) { // valid charge?
                                List<Plea> lChgPlea = cChg.collect("pleas[pleaType==#p1 and pleaDate==#p2]", sPleaType, dPleaEventDate);
                                Plea cChgPlea = lChgPlea.last() ?: new Plea();  // if null create new object
                                logger(((lChgPlea.empty) ? "Adding new" : "Updating existing") + " ChargePlea([$sPleaType]) on charge#($sChgNbr)");

                                // Add charge plea fields
                                cChgPlea.pleaDate = dPleaEventDate;
                                cChgPlea.pleaType = sPleaType;
                                cChgPlea.setAssociatedCharge(cChg);

                                // Add new charge plea to plea collection if new
                                if (lChgPlea.empty)
                                    cChg.pleas.add(cChgPlea);

                                // Commit plea changes
                                cChgPlea.saveOrUpdate();

                            } else
                                logger("No party/charge found for charge#(${sChgNbr}), no charge plea applied");
                        }
                    } else
                        logger( "No charge section found for pleaEvent chargeId(${plea?.@ChargeID})" );
                }
            }

            // ----------------- Add attorney person(s)

            // Add attorney person(s) - Search caseParty for valid atty types DEFT/PLA. If found assign person to Ce_Participants entity
			Set<String> sAttyBaseConnectionCodes = ['AT'];
			Set<String> sAttyCasePartyCodes = ['DEFT','PLA'];

			logger( "<b>Searching for attorney person(s)</b>" );
			for (int cp = 0; cp < xmlIPSluper.Case.CaseParty.size(); cp++) {
				if ( (sAttyCasePartyCodes.any { it == (String)xmlIPSluper.Case.CaseParty[cp].Connection.@Word })) { // valid party baseConnectionCode?

					logger("<b>Found CaseParty [${xmlIPSluper.Case.CaseParty[cp].Connection.@Word}], searching for attys</b>" );
					for (int cpa = 0; cpa < xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney.size(); cpa++) {
						logger("Processing Atty(s) ${cpa+1} of ${xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney.size()}");
						if ((sAttyBaseConnectionCodes.any {it == (String)xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].Connection[0].@'BaseConnection' })) { // valid party atty baseConnectionCode?

							// Find attorney representatives under each CaseParty and add as participant representatives
							String sAttyType= xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].Connection.@Word;
							Person cAttyPerson = getAttyEProsPerson(xmlIPSluper, sAttyType, (String)xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].@'ID');
							if ( cAttyPerson != null ) {

								// Search for existing ePros Ce_Participant either by name or by organization
								logger("Searching for existing atty[$sAttyType] Ce_Participant representative (${cAttyPerson.firstName}, ${cAttyPerson.lastName})");
								Ce_Participant cParticipant= cCase.collect("ce_Participants").find{ p ->
									(p?.person?.lastName == cAttyPerson.lastName && p?.person?.firstName == cAttyPerson.firstName) ||
									 p?.person?.organizationName == cAttyPerson.description
								};

								// Add new case ATTY participant, existing ones are skipped.
								if( cParticipant == null ) { // add new participant?
									logger("Adding atty[$sAttyType] Ce_Participant rep (${cAttyPerson.firstName}, ${cAttyPerson.lastName}) to case");
									cParticipant= new Ce_Participant();
									cParticipant.type= "REP";
                                    cParticipant.subType= "FIRM";
									cParticipant.person= cAttyPerson;
									cParticipant.case= cCase;
									cCase.ce_Participants.add(cParticipant);

								} else
									logger("ePros atty[$sAttyType] Ce_Participant already exists, insert skipped");

								// Determine if ATTY participate needs to be cross referenced
								if( !cParticipant.crossReferenced ) { // no cross reference?
									logger("Attempting to xRef atty[$sAttyType] if party connection available");

									// Set the correct party xRef based on connection type
									Party cPtyXRef = cDefParty; // default to DEF
									if (xmlIPSluper.Case.CaseParty[cp].Connection.@Word == XML_PLAINTIFF_PARTY_TYPE_) { // plaintiff?
										logger("Found xml Plaintiff party for xRef, searching ePros [$XML_PLAINTIFF_PARTY_TYPE_] type");
										CasePartyObj cPLPtyObj= null;
										if ( !(cPLPtyObj= getCaseParty(cCase, xmlIPSluper, XML_PLAINTIFF_PARTY_TYPE_, true)) ) // invalid?
											this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("Found valid xml plaintiffId(${xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].@'ID'}), but no ePros Planitiff party found, check InternalNameID")));
										cPtyXRef = cPLPtyObj?.cParty_;
									}

									// Add Participant/Party xRef if valid
									if (cPtyXRef != null) { // valid ref?
										logger("Adding atty[$sAttyType] Ce_Participant xRef to party[${cPtyXRef.partyType}]");

										// Save/commit new participant obj before the xRef. It must be valid in the Db first before xRef
										cParticipant.saveOrUpdate();

										// Add the participant(LeftObj) Xref to correct party(RightObj) type
                                        if (cParticipant.collect("xrefs[entityType=='Party' and refType=='REPRESENTEDBY'].ref[id == #p1]", cPtyXRef.id).isEmpty()){
											cParticipant.addCrossReference(cPtyXRef, "REPRESENTEDBY");
                                        }
									} else
										logger("Skipping xRef, no valid xRef party found, just updating ATTY Participant");
								}

								// Update inactive/active participant status, remove takes precedence if exists
								if(xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].RemovedDate.size()) {
									cParticipant.status = 'INACT';
									cParticipant.statusDate = convDateStrToDate(xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].RemovedDate.text(), "MM/dd/yyyy");
								} else { // default
									cParticipant.status = 'ACT';
									cParticipant.statusDate = convDateStrToDate(xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].AddedDate.text(), "MM/dd/yyyy");
								}

								// Save participant information
								cParticipant.saveOrUpdate();

							} /*else
								this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("Unable to find an ePros [${xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].Connection.@Word}] person using AttyID(${xmlIPSluper.Case.CaseParty[cp].CasePartyAttorney[cpa].@'ID'}), check xml Bar# or CasePartyName")));*/
						}
					}
				}
			}

            // ----------------- Add scheduled event hearing

			// Add scheduled hearing information, if scheduledEvent already exists add hear
			logger("<b>Adding scheduled hearing events</b>")
			for (int h= 0; h < xmlIPSluper.Case.Hearing.size(); h++) {
				Object oHearing = xmlIPSluper.Case.Hearing[h];
				logger("Processing hearings ${h+1} of ${xmlIPSluper.Case.Hearing.size()} - hearingId(${oHearing.@ID})")

				if(	!StringUtil.isNullOrEmpty((String)oHearing.HearingType.@Word) ) { // valid hearing type?
					String sSchEventType = oHearing.HearingType.@Word;
					if( validateLookupCode(sSchEventType, "EVENT_TYPE") == null )
						this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("ScheduledEvent Type(${sSchEventType}) not found in [EVENT_TYPE] lookupList")));

					// Define hearing attributes
					ScheduledEvent cSchEvent = null;
					List<ScheduledEvent> lSchEvent = [];
					DirLocation cDirLocation= null;

					// Search hearing settings to apply on event
					for (int hs = 0; hs < oHearing.Setting.size(); hs++) {
						logger("Processing Hearing[$h].Settings ID(${oHearing.Setting[hs].@ID}) - ${hs + 1} of ${oHearing.Setting.size()}");

						// Create/Update ScheduledEvent hearing assignment (1 per setting if multiple settings)
						if ( hs == 0 ) { // Only needed once?
							Date dStartDateTime = convDateStrToDate((String) oHearing.Setting[hs].HearingDate.text() +
									(String) oHearing.Setting[hs].CourtSessionBlock.StartTime.text(), "MM/dd/yyyyhh:mm a");

							// Query location for scheduleEvent search (We need this first to use with scheduledEvent query)
							cDirLocation = null;
							String sHearingLocCode = oHearing.Setting[hs].CourtResource.find { l -> l.ResourceType.@Word == 'LOC' }.Code.@Word;
							if (!StringUtil.isNullOrEmpty(sHearingLocCode)) { // valid?
								logger "Search for ePros hearing location [$sHearingLocCode]";
								Where wDirLoc = new Where();
								wDirLoc.addEquals('locationType', 'ROOM');
								wDirLoc.addEquals('code', sHearingLocCode);
								cDirLocation = (DirLocation) DomainObject.find(DirLocation.class, wDirLoc, maxResult(1))[0];
								if (cDirLocation == null) // new?
									this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("Unable to find directory location in ePros for Type(ROOM) - Code($sHearingLocCode)")));
								else
									logger "Found hearing location - Type(ROOM) | Code($sHearingLocCode)";
							} else
								logger "No hearing 'LOC' code found in CourtResource section";

							// Add/Update schedule hearing information
                          logger("1237:ScheduledEvent lookup: type: ${sSchEventType}; startDateTime: ${dStartDateTime}");
							lSchEvent = dStartDateTime != null ? cCase.collect("hearings[type==#p1 and startDateTime==#p2]", sSchEventType, dStartDateTime ) : new RichList();
							cSchEvent = !lSchEvent?.isEmpty() && lSchEvent?.last() != null ? lSchEvent.last() : new ScheduledEvent();	// if null create new object
                          if (cSchEvent.case == null){
                            cCase.add(cSchEvent, "hearings");
                            cCase.saveOrUpdate();
                          }
							logger(((lSchEvent.empty) ? "Adding new" : "Updating existing") + " Hearing [$sSchEventType] | ${oHearing.HearingType.text()}");

							// Add unique event type/comments to original memo if exists
							StringJoiner sMemo = new StringJoiner(" | ");
							if( !StringUtil.isNullOrEmpty(cSchEvent.resultMemoCourt) ) // valid memo, start w/ orig memo
								sMemo.add(cSchEvent.resultMemoCourt);
							for (int hc = 0; hc < oHearing.HearingComment.size(); hc++) {
								if( searchStrForToken(sMemo?.toString(), (String)oHearing.HearingComment[hc].text(), "\\|") == null ) // not in list?
									sMemo.add(oHearing.HearingComment[hc].text());
								else
									logger("Duplicate resultMemoCourt token found, ${oHearing.HearingComment[hc].text()} not added");
							}
							logger("Hearing memo(${sMemo?.toString() ?: ""})");

							// Set hearing attributes
							cSchEvent.type = sSchEventType;
							cSchEvent.resultMemoCourt = sMemo?.toString();
							cSchEvent.startDateTime = dStartDateTime;
							cSchEvent.courtSessionName = oHearing.Setting[hs].CourtSessionName.text();
						}

						// Add all case hearing event information
						if( cSchEvent != null ) {  // valid event
							cSchEvent.saveOrUpdate();
							// Add all case court resource assignments & location
							for (int hsa = 0; hsa < oHearing.Setting[hs].CourtResource.size(); hsa++) {
								Object oCourtResource = oHearing.Setting[hs].CourtResource[hsa];
								logger("Processing Hearing[$h].Settings[$hs].CourtResource - ${hsa + 1} of ${oHearing.Setting[hs].CourtResource.size()}")

								// Determine how to handle the hearing resource type
								String sResourceType = oHearing.Setting[hs].CourtResource[hsa].Type.@Word;
								String sResourceCode = oHearing.Setting[hs].CourtResource[hsa].Code.@Word;
								switch (sResourceType) {
									case 'JUD':
                                  		cSchEvent = getCaseEventAssignment(oCourtResource, cSchEvent, cSubCase, sResourceType, sResourceCode);
										break;
									case 'CR':
										// Add new case assignment role to scheduled event
										cSchEvent = getCaseEventAssignment(oCourtResource, cSchEvent, cSubCase, sResourceType, sResourceCode);
										break;

									case 'LOC':
										if (cDirLocation != null)
											cSchEvent.eventLocation = cDirLocation;
										break;
									default:
										logger("courtResource - Type($sResourceType) | Code($sResourceCode) not used");
										break;
								}
							}
							String ipHearingType = oHearing.HearingType.@Word;
                            Date oHearingDate = convDateStrToDate((String) oHearing.Setting[hs].HearingDate.text(), "MM/dd/yyyy");
                            Timestamp oHearingDateBegin = new Timestamp(oHearingDate.getTime());
                            Timestamp oHearingDateEnd =  Timestamp.valueOf(oHearingDateBegin.toLocalDateTime().withHour(23).withMinute(59));
							// Add hearing court related fields
							logger "Searching for hearing cancelledReason";
							if (oHearing.Setting[hs].CancelledReason?.@Word?.size()) {
								//List<EventResult> lEvtRslt = cCase.collect("hearings[type == #p1 && startDateTime.after(#p2) && startDateTime.before(#p3)].eventResults[eventResultType == #p4]", ipHearingType, oHearingDateBegin, oHearingDateEnd,(String) oHearing.Setting[hs].CancelledReason?.@Word);
                                List<EventResult> lEvtRslt = cSchEvent.collect("eventResults[eventResultType == #p1 && cf_ipFileCaseTitle == #p2]", (String) oHearing.Setting[hs].CancelledReason?.@Word, cDefParty.person.fullName);
								EventResult cEvtRslt = lEvtRslt.last() ?: new EventResult();
								// if null create new object
								logger(((lEvtRslt.empty) ? "Adding new" : "Updating existing") + " hearing cancelled reason [${oHearing.Setting[hs].CancelledReason.text()}]");

								if (validateLookupCode((String) oHearing.Setting[hs].CancelledReason?.@Word, "EVENT_RESULT") == null)
									this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("Hearing cancelled reason type (${oHearing.Setting[hs].CancelledReason?.@Word}) not found in [EVENT_RESULT] lookupList")));

								cEvtRslt.eventResultType = oHearing.Setting[hs].CancelledReason?.@Word;
                              	cEvtRslt.cf_ipFileCaseTitle = cDefParty.person.fullName;
								cEvtRslt.scheduledEvent = cSchEvent;
								cEvtRslt.saveOrUpdate();

								if (lEvtRslt.empty) { // new
									cSchEvent.eventResults.add(cEvtRslt);
								}
							}

							// Add hearing result field
							logger "Searching for hearing result";
							Object oHearingResult = oHearing.Setting[hs].CourtroomMinutes.find { hr -> hr.HearingResult?.@Word?.size() > 0 };
                            //Date oHearingDate = convDateStrToDate((String) oHearing.Setting[hs].HearingDate.text(), "MM/dd/yyyy");
                            //String ipHearingType = oHearing.HearingType.@Word;
							if (oHearingResult.size()) {
								//List<EventResult> lEvtRslt = cCase.collect("hearings[type == #p1 && startDateTime.after(#p2) && startDateTime.before(#p3)].eventResults[eventResultType == #p4]", ipHearingType, oHearingDateBegin, oHearingDateEnd, (String) oHearingResult.HearingResult?.@Word);
                              
                               // List<EventResult> lEvtRslt = cCase.collect("hearings[type == #p1 && startDateTime.after(#p2) && startDateTime.before(#p3)].eventResults[eventResultType == #p4 && memo == #p5]", ipHearingType, oHearingDateBegin, oHearingDateEnd, (String) oHearingResult.HearingResult?.@Word, cDefParty.person.fullName);
                              
                              List<EventResult> lEvtRslt = cSchEvent.collect("eventResults[eventResultType == #p1 && cf_ipFileCaseTitle == #p2]", (String) oHearingResult.HearingResult?.@Word, cDefParty.person.fullName);

								EventResult cEvtRslt = lEvtRslt != null && !lEvtRslt?.isEmpty() ? lEvtRslt.last(): new EventResult();	// if null create new object
								logger(((lEvtRslt == null || lEvtRslt.isEmpty()) ? "Adding new" : "Updating existing") + " hearing result [${oHearingResult.HearingResult.text()}]");
								if (validateLookupCode((String) oHearingResult.HearingResult?.@Word, "EVENT_RESULT") == null)
									this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("Hearing result type (${oHearingResult.HearingResult?.@Word}) not found in [EVENT_RESULT] lookupList")));
								cEvtRslt.eventResultType = oHearingResult.HearingResult?.@Word;
                                cEvtRslt.cf_ipFileCaseTitle = cDefParty.person.fullName;
                                cEvtRslt.eventResultDate = oHearingDate;
								//cEvtRslt.scheduledEvent = cSchEvent;
                                cSchEvent.add(cEvtRslt, "eventResults");
                                cSchEvent.saveOrUpdate();
                                if (cEvtRslt.collect("xrefs[entityType=='Party' and refType=='REFERS_TO'].ref[id == #p1]", cDefParty.id).isEmpty()){
                                	cEvtRslt.addCrossReference(cDefParty, "REFERS_TO");
                                }
								cEvtRslt.saveOrUpdate();
								if (lEvtRslt.empty) { // new
									cSchEvent.eventResults.add(cEvtRslt);
								}
							}
						}	// court resource assignments
					}	// hearing settings
					// Add scheduled event if valid location and assignments made
					if ( cSchEvent != null ) { // valid event?
						cSchEvent.case = cCase;
						cSchEvent.subCase = cSubCase;
                        if (cSchEvent.collect("xrefs[entityType=='Party' and refType=='REFERS_TO'].ref[id == #p1]", cDefParty.id).isEmpty()){
                        	cSchEvent.addCrossReference(cDefParty, "REFERS_TO");
                        }
						cSchEvent.saveOrUpdate();
						if (lSchEvent.empty) // new event?
							cCase.hearings.add(cSchEvent);
					} else
						logger("No valid hearing or assignment person, scheduled event skipped");
				}
			} // hearings

            // ----------------- Add all Case Events

            /**-----------------------------------------------------------------------------------------------------------
             * Add CaseEvents to scheduled event.
			 * Match the CaseEvent to the ParentEvent within the XML, then locate the corresponding ScheduledEvent
             * in ePros and add the EventResult to it. If the ScheduledEvent doesn't already exist, create it using
             * the information from the Hearing in the XML (see above mapping).
             *
             * In the scenario where no ParentEvent can be found, create a ScheduledEvent of type="CRTNHE" and use the
             * EventDate from the XML as the startDateTime.
			 */

            String sCaseEventDefaultType = "CRTNHE";

			if( xmlIPSluper.Case.CaseEvent?.size() ) {

				// Load caseEvents into Hashmap using caseID as the key
				HashMap<Integer, Object> mCaseEvent = new HashMap<Integer, Object>();
				xmlIPSluper.Case.CaseEvent.each { p -> mCaseEvent.put(p.@ID.toInteger(), p); }

				// Sort and process caseEvents
				mCaseEvent.sort { a, b -> a.key <=> b.key }.each { event ->
					Object oCaseEvent= event.getValue();  // assign map event to object tag
					logger("<b>Add caseEvent(${oCaseEvent.@ID}) - [${oCaseEvent.EventType}] to new/existing scheduleEvent</b>");

					// Search hearing section for matching InternalHearingEventID if parentEvent section present
					Object oParentHearing = null;
					String sInternalEventID = oCaseEvent.ParentEvents.ParentEvent.text();
					if (!StringUtil.isNullOrEmpty(sInternalEventID)) { // parent event Id?
						if( xmlIPSluper.Case.Hearing?.size() ) {  // parent hearings?
							println "Searching ${xmlIPSluper.Case.Hearing?.size()} hearing(s) for internalEventID($sInternalEventID)";
							oParentHearing = xmlIPSluper.Case.Hearing.find { h -> h?.@InternalHearingEventID == sInternalEventID };
						} else
							println "No parent hearing(s) found"
					} else
						println "No parentEvent section found"

					// Find existing hearing on the scheduleEvent for caseEvent insert. If the caseEvent has a parent hearing
					// internalEventID then assume the ScheduleEvent for that hearing has already been inserted above.
					List<ScheduledEvent> lSchEvent = [];
					ScheduledEvent cSchEvent = null;
					if ( oParentHearing?.size() ) { // Hearing + scheduledEvent available
						Date dHearingDate = convDateStrToDate((String) oParentHearing.Setting[0].HearingDate.text(), "MM/dd/yyyy");
						String sSchEventType = oParentHearing.HearingType.@Word;

						logger("Parent hearing found, searching ePros for existing sameDay ($dHearingDate) hearing - [$sSchEventType]");
						cSchEvent = cCase.collect("hearings[type==#p1 && startDateTime != null]", sSchEventType).find{ d ->
										DateUtils.isSameDay(d.startDateTime,dHearingDate);
									}
					}
					// As a fail safe, create new "CRTNHE" ScheduledEvent type if one not assigned based on parent hearing
					if (cSchEvent == null) {   // no event?
						Date dSchdEventDate= convDateStrToDate((String) oCaseEvent.EventDate.text(), "MM/dd/yyyy");

						logger("No parent hearing found in xml/epros, searching ePros for default hearing $sCaseEventDefaultType type on EventDate ($dSchdEventDate)");
						List<ScheduledEvent> lDSchEvent = cCase.collect("hearings[type==#p1]", sCaseEventDefaultType).findAll{ d ->
																	DateUtils.isSameDay(d.startDateTime, dSchdEventDate);
										  		  		  }
						cSchEvent = (lDSchEvent.empty)? new ScheduledEvent(): lDSchEvent.last();
						logger(((lDSchEvent.empty) ? "Adding new" : "Updating existing") + " default scheduleEvent [$sCaseEventDefaultType] type");

						cSchEvent.type = sCaseEventDefaultType;
						cSchEvent.startDateTime = dSchdEventDate;
						cSchEvent.case = cCase;
						cSchEvent.subCase = cSubCase;
						cSchEvent.saveOrUpdate();

						if(lDSchEvent.empty) // new
							cCase.hearings.add(cSchEvent);
					} else
						logger("Parent sameDay hearing found");

					// Add event result memo to scheduledEvent hearing
					if( cSchEvent != null ) { // valid?

						// Create/Update existing caseEvent object
						Date dCaseEventDate = convDateStrToDate((String) oCaseEvent.EventDate.text(), "MM/dd/yyyy");
						Timestamp dCaseEventDateBegin = new Timestamp(dCaseEventDate.getTime());
                        Timestamp dCaseEventDateEnd =  Timestamp.valueOf(dCaseEventDateBegin.toLocalDateTime().withHour(23).withMinute(59));
						//List<EventResult> lEventResult = cSchEvent.collect("eventResults[eventResultDate == #p1 && memo == #p2]", dCaseEventDate, cDefParty.person.fullName);
                        //List<EventResult> lEventResult = cSchEvent.collect("eventResults[eventResultDate != null && eventResultDate.after(#p1) && eventResultDate.before(#p2)]", dCaseEventDateBegin, dCaseEventDateEnd);
                      	List<EventResult> lEventResult = cSchEvent.collect("eventResults[cf_ipFileCaseTitle == #p1]", cDefParty.person.fullName);
						EventResult cEventResult = lEventResult != null && !lEventResult?.isEmpty() ? lEventResult.last(): new EventResult();	// if null create new object
						logger(((lEventResult == null || lEventResult.isEmpty()) ? "Adding new" : "Updating existing") + " hearingType [${cSchEvent.type}]");
						// Add unique caseEvent result memo based on EventType + Comment
						StringJoiner sResultMemo = new StringJoiner(" | ");
						String sCaseEventResultMemo = "${oCaseEvent.EventType.text()} ${oCaseEvent.Comment.text()}";
 						logger("Search for existing event resultMemo - [$sCaseEventResultMemo]");
						if (!StringUtil.isNullOrEmpty(cEventResult.resultMemo)) // valid resultMemo, start w/ orig resultMemo
							sResultMemo.add(cEventResult.resultMemo);
						if (!StringUtil.isNullOrEmpty(sCaseEventResultMemo)) {    // valid eventResult to add
							if (searchStrForToken(cEventResult.resultMemo, sCaseEventResultMemo, "\\|") == null) { 	// token not in list, add it?
								logger("Event resultMemo added");
								sResultMemo.add(sCaseEventResultMemo);
							} else
								logger("Duplicate event resultMemo token found, $sCaseEventResultMemo not added");
						}
						// Add caseEvent elements
						cEventResult.resultMemo = sResultMemo?.toString();
                        cEventResult.cf_ipFileCaseTitle = cDefParty.person.fullName;
						cEventResult.eventResultDate = dCaseEventDate;
                        //cEventResult.eventResultType = ipHearingResultType;
						cEventResult.scheduledEvent = cSchEvent;
                      if (cEventResult.collect("xrefs[entityType=='Party' and refType=='REFERS_TO'].ref[id == #p1]", cDefParty.id).isEmpty()){
                        	cEventResult.addCrossReference(cDefParty, "REFERS_TO");
                      }
                      if (oCaseEvent.EventType.@Word == 'APDA'){
                        	cSchEvent.assigneeName = oCaseEvent.Comment.text();
                      }                      
						cEventResult.saveOrUpdate();

						// Add new caseEvent if needed
						if (lEventResult.empty)
							cSchEvent.eventResults.add(cEventResult);
						cSchEvent.saveOrUpdate();

					} else
						logger("No valid ScheduleEvent, eventResult insert skipped");
				}
			}

			// ----------------- Add all bail/bonds

			if( xmlIPSluper.Bond.size() ) { // valid Bond section?
              for (int i = 0; i <= xmlIPSluper.Bond.size(); i++){
				logger("<b>Adding party bail</b>" );
				String sBailType = xmlIPSluper.Bond[i].Type.@Word;
				if( validateLookupCode(sBailType, "BAIL_TYPE") == null )
                    this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, fFilePath.getName(), logger("BailType(${xmlIPSluper.Bond[i].Type.@Word}) not found in [BAIL_TYPE] lookupList")));

                List<Bail> lBails = cDefParty.collect("bails[bailType == #p1]", sBailType)
                Bail cBail = lBails.last() ?: new Bail(); // if null create new object

                logger(((!lBails) ? "Adding new" : "Updating existing") + " bail");
                cBail.bailType = sBailType;
				if (!StringUtil.isNullOrEmpty(xmlIPSluper.Bond[i].Amount.text())
					&& xmlIPSluper.Bond[i].Amount.text() != false ) {
					cBail.bailAmountCents = usdToCents(xmlIPSluper.Bond[i].Amount.text());
				}
                cBail.setAssociatedParty(cDefParty);

                // Add new Bail/Bond if required
                logger("BailType(${sBailType}) - Amount(${cBail.bailAmountCents})");
                if (lBails.empty) // create new?
                    cDefParty.bails.add(cBail);
            	}
			}

            // ----------------- Update Case/party

			logger("<b>Saving all pre/post-domain objects + case(${cCase.caseNumber})</b>")
			cCase.saveOrUpdate(); 	// submit/resubmit case
			bRetVal = true;

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::ProcessIPFile - Error processing ${fFilePath.getName()} IP file");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return bRetVal;
	}

	/** -----------------------------------------------------------------------------------------------------
	 * Add all sentence comment components
	 * @param cSentence - active sentence
	 * @param oSentEvent - parent xml element
	 * @returns active sentence
	 */
	public Sentence addSentCommentComponents( Sentence cSentence, Object oSentEvent ) {
		if( cSentence == null ) return cSentence;

		// Add all comments to sentenceMethod collection
		for (int cmt = 0; cmt < oSentEvent.Sentence.Additional.CommentComponent.size(); cmt++) {
			List<SentenceMethod> lSentMethods = cSentence.collect("sentenceMethods[sentenceMethodType=='COM' && memo==#p1]", (String)oSentEvent.Sentence.Additional.CommentComponent[cmt].text());
			SentenceMethod cSentMethod = lSentMethods.last() ?: new SentenceMethod(); // if null create new object
			logger(((lSentMethods.empty) ? "Adding new" : "Updating existing") + " sentence comment component");
			cSentMethod.sentenceMethodType = 'COM';
			cSentMethod.memo = (String) oSentEvent.Sentence.Additional.CommentComponent[cmt].text();
			cSentMethod.sentence= cSentence;
			if (lSentMethods.empty)
				cSentence.sentenceMethods.add(cSentMethod);

			logger("Sentence comment[$cmt] component(${(String) oSentEvent.Sentence.Additional.CommentComponent[cmt].text()})");
		}
		return cSentence;
	}

	/** -----------------------------------------------------------------------------------------------------
     *  Add ConditionComponents and FeeSchedules to sentence on Charge. The sentence will contain only the
     *  ConditionComponent / Scheduled Fee information.
	 * @param cCharge
     * @param cDefParty
     * @param oSentEvent - parent element
     * @param mChgSentHdr - map of parent params
     * @returns active party w/ charge/sentence changes
     */
    public Party addConditionComponentAndScheduledFees( Charge cChg, Party cDefParty, Object oSentEvent, Map mChgSentHdr ) {

        try {
            logger("<b>Search for conditionComponents + Fee schedules to insert</b>");
  	        if (cChg != null) { // valid charge?

				// Add conditionComponents + Fee schedules
              if( oSentEvent.Sentence.Additional.ConditionComponent.Condition.size() || oSentEvent.Sentence.Additional.Fees.size() ) {

					// Search for exisiting parent sentence to use for condition inserts, it must be a sentence that's not a confinement or probation type
					logger("Search for sentenceType(${mChgSentHdr.type}) for conditionComponents + fees");

					// Create parent sentence to hold the ConditionComponents and FeeSchedules
                if (oSentEvent.Sentence.Additional.ConditionComponent.Condition.size()){
                    Sentence cSent = null;
					//List<Sentence> lSent = cChg.collect("sentences[(sentenceType==#p1 and sentenceDate==#p2) and cf_sentenceOption==null and supervisionType==null]", mChgSentHdr.type, mChgSentHdr.date);
                  	List<Sentence> lSent = cChg.collect("sentences[!conditions?.isEmpty() && status == 'COND']");
                  	//lSent = lSent?.isEmpty() ? cChg.collect("sentences[status == 'CONF']") : lSent;
					cSent = lSent.last() ?: new Sentence(); logger("new Sentence: 1565:");
					logger(((!lSent) ? "Adding new" : "Updating existing") + " sentence for conditionComponents + fees if needed");

					// Add sentence comment components
					//cSent= addSentCommentComponents( cSent, oSentEvent );

					// Add parent sentence fields
                    cSent.status = "COND";
					cSent.sentenceType = mChgSentHdr.type;
					cSent.sentenceDate = mChgSentHdr.date;
					cSent.setAssociatedCharge(cChg);
					cSent.description = "1423";
					cSent.setAssociatedCharge(cChg);
                  cSent.saveOrUpdate();  logger("new Sentence: 1565: saved ${cSent}");
					// Add all sentence condition components
					for (int cc = 0; cc < oSentEvent.Sentence.Additional.ConditionComponent.Condition.size(); cc++) {
						Object oCondSection = oSentEvent.Sentence.Additional.ConditionComponent.Condition[cc];

						logger("Search for sentenceType(${mChgSentHdr.type}) to add conditionComponent(${oCondSection.Type.@Word})");

						// Validate condition component type/status
						String sConditionType= oCondSection.Type.@Word;
						if (validateLookupCode(sConditionType, "SENTENCE_CONDITION_TYPE") == null)
							this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.ConditionComponent.Condition[$cc].Type($sConditionType) not found in LookupList[SENTENCE_CONDITION_TYPE]")));
						String sConditionStatus= oCondSection.Status.StatusType.@Word;
						if (validateLookupCode(sConditionStatus, "SENTENCE_CONDITION_STATUS") == null)
							this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.ConditionComponent.Condition[$cc].Status($sConditionStatus) not found in LookupList[SENTENCE_CONDITION_STATUS]")));

						// Create/Update condition component
						Date dEffectiveDate = convDateStrToDate((String)oCondSection.EffectiveDate.text(), "MM/dd/yyyy");
						logger(" Sentence condition - Type($sConditionType), Date($dEffectiveDate)");

						List<SentenceCondition> lSentCondComp = cSent.collect("conditions[sentenceConditionType==#p1 and sentenceConditionBeginDate==#p2]", sConditionType, dEffectiveDate);
						SentenceCondition cSentCondComp = lSentCondComp.last() ?: new SentenceCondition();
						logger(((lSentCondComp.empty) ? "Adding new" : "Updating existing") + " [${cc + 1} of ${oSentEvent.Sentence.Additional.ConditionComponent.Condition.size()}] - sentenceCondition(s)");

						cSentCondComp.sentenceConditionType = sConditionType;
						cSentCondComp.sentenceConditionBeginDate = dEffectiveDate;

						// Validate condition component status type
						cSentCondComp.status = sConditionStatus;
						cSentCondComp.statusDate = convDateStrToDate((String) oCondSection?.Status?.StatusDate, "MM/dd/yyyy");
						//cSentCondComp.setAssociatedSentence(cSent);
						cSentCondComp.associatedSentence = cSent;
                        cSent.add(cSentCondComp, "conditions");
                        cSent.saveOrUpdate();
						logger("Sentence condition status - StatusType(${cSentCondComp.status}), StatusDate(${cSentCondComp.statusDate})");

					}
              }
					// ------------------ Add scheduled fee component to sentence

					logger("<b>Search for addFeeScheduleComponents to insert</b>");

					// Add Charge Sentence fees
                if (oSentEvent.Sentence.Additional.Fees.size()){
					for (int f = 0; f < oSentEvent.Sentence.Additional.Fees.size(); f++) {
                      Sentence cSent1 = null;
                      //List<Sentence> lSent1 = cChg.collect("sentences[!feeSchedules?.isEmpty() && status == 'FEES']");
                      ArrayList lSent1 = DomainObject.find(Sentence.class, "associatedCharge.id", cChg.id, "status", "FEES", "sentenceType", mChgSentHdr.type);
                      cSent1 = !lSent1?.isEmpty() ? lSent1.find({thisSentence -> thisSentence != null}) : new Sentence(); logger("new Sentence: 1625:");
                    cSent1.status = "FEES";
					cSent1.sentenceType = mChgSentHdr.type;
					cSent1.sentenceDate = mChgSentHdr.date;
					cSent1.setAssociatedCharge(cChg);
						for (int fs = 0; fs < oSentEvent.Sentence.Additional.Fees[f].FeeSchedule.size(); fs++) {
							Object oFeeSch = oSentEvent.Sentence.Additional.Fees[f].FeeSchedule[fs];

							String sFeeSentType = oFeeSch.FeeSchedule.@Word;
							if (validateLookupCode(sFeeSentType, "FEE_SCHEDULE") == null)
								this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.Fees.FeeSchedule[$fs].feeType(${sFeeSentType}) not found in LookupList[FEE_SCHEDULE]")));

							// Add/Update feeSchedule
							//List<Ce_FeeSchedule> lSentEvtFee = cSent.collect("feeSchedules[feeType==#p1]", sFeeSentType);
							//Ce_FeeSchedule cSentEvtFee = lSentEvtFee.last() ?: new Ce_FeeSchedule();	// if null create new object
							//logger(((lSentEvtFee.empty) ? "Adding new" : "Updating existing") + " [${fs + 1} of ${oSentEvent.Sentence.Additional.Fees[f].FeeSchedule.size()}] - feeSchedule(s)");

							if ( !StringUtil.isNullOrEmpty(oFeeSch.FeeAmount?.text()) && oFeeSch.FeeAmount?.text() != "false"){
							List<Ce_FeeSchedule> lSentEvtFee = cSent1.collect("feeSchedules[feeType==#p1]", sFeeSentType);
                            
                            String feeAmountString = "";
                            Double feeAmountDouble = 0.00;
                            oFeeSch.FeeAmount?.text().each({it -> 
  							if (it == '.' || it.matches("\\d")){feeAmountString += it}
							});
                            feeAmountDouble = Double.parseDouble(feeAmountString);
                            lSentEvtFee = lSentEvtFee == null || lSentEvtFee?.isEmpty() ? cSent1.collect("feeSchedules[feeAmountCents == #p1]", feeAmountDouble) : lSentEvtFee;
                            //lSentEvtFee = lSentEvtFee == null || lSentEvtFee?.isEmpty() ? DomainObject.find(Ce_FeeSchedule.class, "sentence.id", cSent.id, "feeAmountCents", feeAmountDouble) : lSentEvtFee;
                            Ce_FeeSchedule cSentEvtFee = lSentEvtFee.last() ?: new Ce_FeeSchedule();
                            cSentEvtFee.feeType = sFeeSentType;
                            cSentEvtFee.feeAmountCents = feeAmountDouble; //usdToCents(oFeeSch.FeeAmount?.text());
							cSentEvtFee.feeWaived = oFeeSch.Waived.text()?.toBoolean();
                              if (cDefParty == null){
                                logger("cDefParty is null")
                              }
							cSentEvtFee.case = cDefParty.case;
							cSentEvtFee.sentence = cSent1;
							cSentEvtFee.saveOrUpdate();
							cSent1.add(cSentEvtFee, "feeSchedules");
                              cSent1.saveOrUpdate(); logger("new Sentence: 1625: saved ${cSent1}");
							logger("Sentence Fee - Type(${cSentEvtFee.feeType}), FeeAmount(${cSentEvtFee.feeAmountCents}) - FeeWaived(${cSentEvtFee.feeWaived}) ${feeAmountDouble}");
                            }
						}
					}
              }

				} else
					logger("No ConditionComponent or ScheduledFee sentences found");
            } else
                logger("No valid ConditionComponent or ScheduledFee charge found");

        } catch (Exception ex) {
            logger iTracking_.setException(ex.message, "Exception::addConditionComponentAndScheduledFees - ConditionComponent / fee insert error");
            iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
        }

        return cDefParty;
    }

    /** ----------------------------------------------------------------------------------------------
     * Add CAConfinementComponent sentences to Charge
	 * @param cCharge
     * @param cDefParty
	 * @param oSentEvent - parent element
	 * @param mChgSentHdr - map of parent params
     * @returns active party w/ charge/sentence changes
     */
    public Party addCAConfinementComponent( Charge cChg, Party cDefParty, Object oSentEvent, Map mChgSentHdr ) {

        try {
          logger("<b>Search for CAConfinementComponents to insert; 1:cChg:${cChg}; 2:cDefParty:${cDefParty}; 3:oSentEvent:${oSentEvent}; 4:mChgSentHdr:${mChgSentHdr}</b>");
            if ( cChg != null ) { // valid charge?
 logger("addCAConfinementComponent:1");
                // Add/Update CAConfinementComponent sentence
                for (int sc = 0; sc < oSentEvent.Sentence.Additional.CAConfinementComponent.size(); sc++) {
                    Object oConfSection = oSentEvent.Sentence.Additional.CAConfinementComponent[sc]; // set pointer to tag element
logger("addCAConfinementComponent:2");
                    // Validate confinement component types
					String sSentenceOpt= (String)oConfSection.Type.@Word;
                    if (validateLookupCode(sSentenceOpt, "SENTENCE_OPTION") == null)
                        this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.CAConfinementComponent[$sc].Type(${sSentenceOpt}) not found in LookupList[SENTENCE_OPTION]")));
					String sTermTypeCode= oConfSection.TermType.@Word;
					if (validateLookupCode(sTermTypeCode, "CONFINEMENT_TERM_TYPE") == null)
						this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.CAConfinementComponent[$sc].TermType($sTermTypeCode) not found in LookupList[CONFINEMENT_TERM_TYPE]")));

					//List<Sentence> lSentEvtConf = cChg.collect("sentences[(sentenceType==#p1 and sentenceDate==#p2) and cf_sentenceOption==#p3 and cf_confinementTermType==#p4]", "CONF", mChgSentHdr.date, sSentenceOpt, sTermTypeCode);
                  	List<Sentence> lSentEvtConf = cChg.collect("sentences[sentenceDate==#p1 and cf_sentenceOption==#p2 and cf_confinementTermType==#p3]", mChgSentHdr.date, sSentenceOpt, sTermTypeCode);

                    Sentence cSentEvtConf = lSentEvtConf.last() ?: new Sentence(); logger("new Sentence: 1713:");
					logger(((lSentEvtConf.empty) ? "<b>Adding new" : "Updating existing") + " sentenceCAConfinement [${sc+1} of ${oSentEvent.Sentence.Additional.CAConfinementComponent.size()}] - Option(${sSentenceOpt})</b>");

					// Add sentence comment components
					//cSentEvtConf= addSentCommentComponents( cSentEvtConf, oSentEvent );

                  	cSentEvtConf.status = "CONF";
                    cSentEvtConf.associatedCharge = cChg;
                  	cSentEvtConf.memo = "${oConfSection.StayedReason.text()}".toString();
                    cSentEvtConf.memo += "${oConfSection.SuspendedReason.text()}".toString();
                  	cSentEvtConf.description = "1550";
					cSentEvtConf.sentenceType = mChgSentHdr.type;
                    cSentEvtConf.sentenceDate = mChgSentHdr.date;
                    cSentEvtConf.cf_sentenceOption = sSentenceOpt; 
                  cSentEvtConf.saveOrUpdate(); logger("new Sentence: 1713: saved ${cSentEvtConf}");
					if (!StringUtil.isNullOrEmpty(oConfSection.StartDate.text()))   // valid start date?
                        cSentEvtConf.sentenceBeginDate = convDateStrToDate((String)oConfSection.StartDate.text(), "MM/dd/yyyy");
                    else // use sentencing start date if not available in xml
                        cSentEvtConf.sentenceBeginDate = cSentEvtConf.sentenceDate;

                   	cSentEvtConf.cf_impositionSuspConfine = oConfSection.ImpositionOfSentenceSuspended.text()?.toBoolean();
					cSentEvtConf.cf_sentenceStayed = oConfSection.SentencingStayed.text()?.toBoolean();
                    cSentEvtConf.cf_concurrentWith = oConfSection.ConcurrentWith.text()?.toBoolean();
                    cSentEvtConf.cf_concurrentComment = oConfSection.ConcurrentComment.text();
                    cSentEvtConf.cf_consecutiveWith = oConfSection.ConsecutiveWith.text()?.toBoolean();
                    cSentEvtConf.cf_consecutiveComment = oConfSection.ConsecutiveComment.text();
                  logger("addCAConfinementComponent:3");
                  
                    // Validate confinement term type
                    cSentEvtConf.cf_confinementTermType = sTermTypeCode;
                    if (!StringUtil.isNullOrEmpty(oConfSection.TermDuration.Years.text()))
                        cSentEvtConf.cf_sentenceYears = oConfSection.TermDuration.Years.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.TermDuration.Months.text()))
                        cSentEvtConf.cf_sentenceMonths = oConfSection.TermDuration.Months.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.TermDuration.Days.text()))
                        cSentEvtConf.cf_sentenceDays = oConfSection.TermDuration.Days.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.StayedDuration.Years.text()))
                        cSentEvtConf.cf_sentenceStayedYears = oConfSection.StayedDuration.Years.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.StayedDuration.Months.text()))
                        cSentEvtConf.cf_sentenceStayedMonths = oConfSection.StayedDuration.Months.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.StayedDuration.Days.text()))
                        cSentEvtConf.cf_sentenceStayedDays = oConfSection.StayedDuration.Days.text()?.replaceAll("[^0-9.]", "").toDouble();
logger("addCAConfinementComponent:4");
                    //Suspended Duration
                    if (!StringUtil.isNullOrEmpty(oConfSection.SuspendedDuration.Years.text()))
                        cSentEvtConf.cf_sentenceStayedYears = oConfSection.SuspendedDuration.Years.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.SuspendedDuration.Months.text()))
                        cSentEvtConf.cf_sentenceStayedMonths = oConfSection.SuspendedDuration.Months.text()?.replaceAll("[^0-9.]", "").toDouble();
                    if (!StringUtil.isNullOrEmpty(oConfSection.SuspendedDuration.Days.text()))
                        cSentEvtConf.cf_sentenceStayedDays = oConfSection.SuspendedDuration.Days.text()?.replaceAll("[^0-9.]", "").toDouble();
                  
                    // Set confinement reason (one per customer)
                    if (oConfSection.YearsToLife.text().toBoolean())
                        cSentEvtConf.cf_confinementOption = "TOLIFE";
					else if (oConfSection.Life.text().toBoolean())
                        cSentEvtConf.cf_confinementOption = "LIFE";
					else if (oConfSection.Death.text().toBoolean())
                        cSentEvtConf.cf_confinementOption = "DP";
					else if (oConfSection.UntilAge18.text().toBoolean())
                        cSentEvtConf.cf_confinementOption = "TO18";
					else if (oConfSection.UntilAge21.text().toBoolean())
                        cSentEvtConf.cf_confinementOption = "TO21";
					else if (oConfSection.UntilAge25.text().toBoolean())
                        cSentEvtConf.cf_confinementOption = "TO25";
				    cSentEvtConf.cf_FineLieuJailTime = oConfSection.PayFineInLieuOfJailTime.text()?.toBoolean();
logger("addCAConfinementComponent:5");
                    if ( !StringUtil.isNullOrEmpty(oConfSection.FineSuspendedAmount.text())
						 && oConfSection.FineSuspendedAmount.text()	!= 'false' ) {
                      logger("addCAConfinementComponent:51;oConfSection.FineSuspendedAmount.text():${oConfSection.FineSuspendedAmountValue.text()}");
						cSentEvtConf.cf_suspendedFineFeeCents = usdToCents(oConfSection.FineSuspendedAmountValue.text());
                      logger("addCAConfinementComponent:52");
					}
                  logger("addCAConfinementComponent:53");
                    cSentEvtConf.setAssociatedCharge(cChg);
logger("addCAConfinementComponent:6");
                    // Add new CAConfinementComponent to sentences
                    if (lSentEvtConf.empty)   // new entry
                        cChg.sentences.add(cSentEvtConf);
                }

            } else
                logger("No valid CAConfinementComponents sentence charge found");

        } catch (Exception ex) {
          //logger("Exception::addCAConfinementComponent: ${ex.getMessage()}");
            logger iTracking_.setException(ex.message, "Exception::addCAConfinementComponent - insert error");
            iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
        }

        return cDefParty;
    }
  /** ----------------------------------------------------------------------------------------------
     * Add ProgramComponet sentences to Charge
     */
  public void addProgramComponent( Charge cChg, Party cDefParty, Object oSentEvent, Map mChgSentHdr ) {
    try{
      if ( cChg != null ) {
        for (int sc = 0; sc < oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program.size(); sc++) {
          logger("1805: ProgramType: ${oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].ProgramType.@Word}")
          Object oProgramComponent = oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc];
          ArrayList programComponents = DomainObject.find(Sentence.class, "associatedCharge.id", cChg.id, "status", "PROG", "sentenceType", "SENT");
          Sentence programComponent = !programComponents?.isEmpty() ? programComponents.last() : new Sentence(); logger("new Sentence: 1810:");
          programComponent.associatedCharge = cChg;
          programComponent.status = "PROG";
          programComponent.sentenceType = mChgSentHdr.type; //"SENT";
          programComponent.sentenceDate = mChgSentHdr.date;
          programComponent.saveOrUpdate();
          ArrayList programComponentConditions;
          String programComment = oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].Comment.text() != null && !oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].Comment.text().isEmpty() ? oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].Comment.text() : "";
          Date programDate = convDateStrToDate(oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].OrderDate.text(), "MM/dd/yyyy"); 
          String programType = oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].ProgramType.@Word; logger("1817:");
          String programTypeText = oSentEvent.Sentence.Additional.CAProgramsComponent.Programs.Program[sc].ProgramType;
          if (!programComment.trim().isEmpty()){
            //programComponentConditions = programComponent.collect("conditions[memo == #p1 && sentenceConditionBeginDate == #p2 && sentenceConditionType == #p3]", programComment, programDate, programType).orderBy("id");
            programComponentConditions = DomainObject.find(SentenceCondition.class, "associatedSentence", programComponent.id, "memo", programComment, "sentenceConditionBeginDate", programDate, "sentenceConditionType", programType);logger("1820:");
          } else{
            //programComponentConditions = programComponent.collect("conditions[sentenceConditionBeginDate == #p1 && sentenceConditionType == #p2]", programDate, programType).orderBy("id");
            programComponentConditions = DomainObject.find(SentenceCondition.class, "associatedSentence", programComponent.id, "sentenceConditionBeginDate", programDate, "sentenceConditionType", programType); logger("1823:");
          }
          //programComponentConditions?.isEmpty() ?  DomainObject.find(SentenceCondition.class, "associatedSentence", programComponent, "sentenceConditionType", programType.toString(), "message", programComment.toString(), "status", "ACT", "statusDate", programDate) : programComponentConditions;
          logger("1826: associatedSentence: ${programComponent}")
          programComponentConditions?.isEmpty() ?  DomainObject.find(SentenceCondition.class, "associatedSentence", programComponent, "status", "ACT", "statusDate", programDate) : programComponentConditions; logger("1827:");
          SentenceCondition programSentenceCondition = !programComponentConditions?.isEmpty() ? programComponentConditions.find({thisCompCond -> thisCompCond.sentenceConditionType == programType.toString() && thisCompCond.message == programComment.toString()}) : new SentenceCondition();
          logger("1829: new sentence condition? ${programSentenceCondition.id}; programType: ${programSentenceCondition.sentenceConditionType}")
          //programSentenceCondition.memo = programComment;
          programSentenceCondition.message = "${programComment}"; //${programTypeText}
          programSentenceCondition.sentenceConditionBeginDate = programDate; logger("1830:");
          programSentenceCondition.status = "ACT";
          programSentenceCondition.statusDate = programDate;
          programSentenceCondition.sentenceConditionType = programType;
          programSentenceCondition.associatedSentence = programComponent;
          programSentenceCondition.saveOrUpdate();
          programComponent.add(programSentenceCondition, "conditions");
          programComponent.saveOrUpdate(); logger("new Sentence: 1810: saved ${programComponent}");
        }
      }
    }catch(Exception programEx){
      logger("1843: exception with program component: ${programEx.getMessage()}; ${programEx.getCause()}");
    }
  }
      /** ----------------------------------------------------------------------------------------------
     * Add Converted Disposition Component
     */
    public void addConvertedDispositionComponent( Charge cChg, Party cDefParty, Object oSentEvent, Map mChgSentHdr ) {

        try {
            logger("<b>Search for Converted Disposition to insert </b>");
            if ( cChg != null ) { // valid charge?

                // Add/Update CommentComponent sentence
                for (int sc = 0; sc < oSentEvent.Sentence.Additional.ConvertedDispositionComponent.size(); sc++) {
                    Object oConvertedDispositionComponent = oSentEvent.Sentence.Additional.ConvertedDispositionComponent[sc];
                  	ArrayList commentSentences = DomainObject.find(Sentence.class, "associatedCharge.id", cChg.id, "status", "CONVDISPO");
					Sentence cSentComment = !commentSentences?.isEmpty() ? commentSentences.last() : new Sentence(); logger("new Sentence: 1862:");
                  	cSentComment.associatedCharge = cChg;
                  	cSentComment.status = "CONVDISPO";
                    cSentComment.sentenceType = mChgSentHdr.type;
                    cSentComment.sentenceDate = mChgSentHdr.date;
                  cSentComment.saveOrUpdate(); logger("new Sentence: 1862: saved ${cSentComment}");
                  logger("oConvertedDispositionComponent: ${oConvertedDispositionComponent}; ${oConvertedDispositionComponent.Value}")
                  	List<SentenceMethod> sentenceMethods = cSentComment.collect("sentenceMethods[sentenceMethodType=='COM' && memo==#p1]", oConvertedDispositionComponent.Value.text());
                  if (oConvertedDispositionComponent.Value.text() != null){
                  	SentenceMethod sentenceMethod = sentenceMethods.last() ?: new SentenceMethod();
					sentenceMethod.sentenceMethodType = "COM";
					sentenceMethod.memo = oConvertedDispositionComponent.Value.text();
					sentenceMethod.sentence = cSentComment;
                  	sentenceMethod.saveOrUpdate();
                	}
                }

            }
        } catch (Exception ex) {
          	logger("exception with commentcomponent ${ex.getMessage()}");
            //logger iTracking_.setException(ex.message, "Exception::addCAConfinementComponent - insert error");
            //iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
        }
    }
    /** ----------------------------------------------------------------------------------------------
     * Add CommentComponet sentences to Charge
     */
    public void addCommentComponent( Charge cChg, Party cDefParty, Object oSentEvent, Map mChgSentHdr ) {

        try {
            logger("<b>Search for CommentComponent to insert </b>");
            if ( cChg != null ) { // valid charge?

                // Add/Update CommentComponent sentence
                for (int sc = 0; sc < oSentEvent.Sentence.Additional.CommentComponent.size(); sc++) {
                    Object oCommentComponent = oSentEvent.Sentence.Additional.CommentComponent[sc];
                  	ArrayList commentSentences = DomainObject.find(Sentence.class, "associatedCharge.id", cChg.id, "status", "COM", "sentenceDate", mChgSentHdr.date, "sentenceType", mChgSentHdr.type);
					Sentence cSentComment = !commentSentences?.isEmpty() ? commentSentences.last() : new Sentence(); logger("new Sentence: 1899:");
                  	cSentComment.associatedCharge = cChg;
                  	cSentComment.status = "COM";
                    cSentComment.sentenceType = mChgSentHdr.type;
                    cSentComment.sentenceDate = mChgSentHdr.date;
                  cSentComment.saveOrUpdate(); logger("new Sentence: 1899: saved ${cSentComment}");
					List<SentenceMethod> sentenceMethods = cSentComment.collect("sentenceMethods[sentenceMethodType=='COM' && memo==#p1]", oCommentComponent.Comment.text());
                    ArrayList sentenceMethodsArrayList = DomainObject.find(SentenceMethod.class, "sentence", cSentComment, "sentenceMethodType", "COM", "memo", oCommentComponent.Comment.text());
                  
                  if (oCommentComponent.Comment.text() != null || !oCommentComponent.Comment.text().trim().isEmpty()){
                  	SentenceMethod sentenceMethod = !sentenceMethodsArrayList?.isEmpty() ? sentenceMethodsArrayList.last(): new SentenceMethod();
					sentenceMethod.sentenceMethodType = "COM";
					sentenceMethod.memo = oCommentComponent.Comment.text();
					sentenceMethod.sentence = cSentComment;
                  	sentenceMethod.saveOrUpdate();
                	}
                }

            }
        } catch (Exception ex) {
          	logger("exception with commentcomponent ${ex.getMessage()}");
            //logger iTracking_.setException(ex.message, "Exception::addCAConfinementComponent - insert error");
            //iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
        }
    }
    /** ----------------------------------------------------------------------------------------------
     * Add CAProbationComponent sentences to Charge
	 * @param cCharge
     * @param cDefParty
	 * @param oSentEvent - parent element
	 * @param mChgSentHdr - map of parent params
     * @returns active party w/ charge/sentence changes
     */
    public Party addCAProbationComponent( Charge cChg, Party cDefParty, Object oSentEvent, Map mChgSentHdr ) {

        try {
            logger("<b>Search for addCAProbationComponents to insert</b>");
            if (cChg != null) { // valid charge?

                // Add/Update CAConfinementComponent sentence
                for (int sp = 0; sp < oSentEvent.Sentence.Additional.CAProbationComponent.size(); sp++) {
                    Object oProbSection = oSentEvent.Sentence.Additional.CAProbationComponent[sp];  // set pointer to tag element

                    // Validate probation type
					String sProbSuperVisionType = (String)oProbSection.Type.@Word;
                    if (validateLookupCode(sProbSuperVisionType, "SUPERVISION_TYPE") == null)
                        this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.CAProbationComponent[$sc].Type(${sProbSuperVisionType}) not found in LookupList[SUPERVISION_TYPE]")));

                    // Search for exisiting sentence
                    List<Sentence> lSentEvtProb = cChg.collect("sentences[(sentenceType==#p1 and sentenceDate==#p2) and supervisionType==#p3]", mChgSentHdr.type, mChgSentHdr.date, sProbSuperVisionType);
                    Sentence cSentEvtProb = lSentEvtProb.last() ?: new Sentence(); logger("new Sentence: 1949: ${cSentEvtProb}");
                    logger(((!lSentEvtProb) ? "Adding new" : "Updating existing") + " sentenceCAProbation [${sp+1} of ${oSentEvent.Sentence.Additional.CAProbationComponent.size()}] - SuperVisionType(${sProbSuperVisionType})");

					// Add sentence comment components
					//cSentEvtProb= addSentCommentComponents( cSentEvtProb, oSentEvent );
										
					cSentEvtProb.status =  "SUP";//cSentEvtProb.collect("conditions").isEmpty() ? "SUP": "COND";
					cSentEvtProb.sentenceType = mChgSentHdr.type;
                    cSentEvtProb.sentenceDate = mChgSentHdr.date;
                  	cSentEvtProb.description = "1682";
                    cSentEvtProb.supervisionType = sProbSuperVisionType;
                    cSentEvtProb.associatedCharge = cChg;
                    cSentEvtProb.saveOrUpdate();
                    cChg.add(cSentEvtProb, "sentences");
                    cChg.saveOrUpdate();
                    // Probation supervision time/date
					if (!StringUtil.isNullOrEmpty(oProbSection.Term.Years.text()))
                        cSentEvtProb.cf_supervisionYears = oProbSection.Term.Years.text().replaceAll("[^0-9.]", "").toDouble();
					if (!StringUtil.isNullOrEmpty(oProbSection.Term.Months.text()))
                        cSentEvtProb.cf_supervisionMonths = oProbSection.Term.Months.text().replaceAll("[^0-9.]", "").toDouble();
					if (!StringUtil.isNullOrEmpty(oProbSection.Term.Days.text()))
                        cSentEvtProb.cf_supervisionDays = oProbSection.Term.Days.text().replaceAll("[^0-9.]", "").toDouble();

 					cSentEvtProb.supervisionStartDate = convDateStrToDate((String)oProbSection.StartDate.text(), "MM/dd/yyyy");
					cSentEvtProb.supervisionEndDate = convDateStrToDate((String)oProbSection.EndDate.text(), "MM/dd/yyyy");
					cSentEvtProb.cf_impositionSuspended = oProbSection.ImpositionOfSentenceSuspended.text().toBoolean();

                    logger("CAProbationComponent[$sp] Sentence - Type(${cSentEvtProb.sentenceType}), Date(${cSentEvtProb.sentenceDate})");

					// Add Dispo status collection
					for (int ds = 0; ds < oSentEvent.Sentence.Additional.CAProbationComponent[sp].DispositionStatusCollections.DispositionStatusCollection.size(); ds++) {
						Object oDispoStatus= oSentEvent.Sentence.Additional.CAProbationComponent[sp].DispositionStatusCollections.DispositionStatusCollection[ds];

						// Test dispo statusId
						String sDispoStatId= oDispoStatus.StatusID.@Word;
						if (validateLookupCode(sDispoStatId, "SENTENCE_METHOD_STATUS") == null)
							this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("CAProbationComponent[$sp] DispositionStatusCollection[$ds] not found in LookupList[SENTENCE_METHOD_STATUS]")));

						// Add dispo status/date
						if( !StringUtil.isNullOrEmpty(sDispoStatId) ) { // valid statusId
							Date dDispoStatusDate = convDateStrToDate((String)oDispoStatus.Date.text(),"MM/dd/yyyy");
							List<SentenceMethod> lSentDispoStat = cSentEvtProb.collect("sentenceMethods[sentenceMethodType == 'DISPO' && status == #p1 && statusDate == #p2]", sDispoStatId, dDispoStatusDate);
                          ArrayList lSentDispoStatArrayList = DomainObject.find(SentenceMethod.class, "sentence", cSentEvtProb, "sentenceMethodType", "DISPO", "status", sDispoStatId, "statusDate", dDispoStatusDate)
							//SentenceMethod cSentDispoStat = lSentDispoStat.last() ?: new SentenceMethod(); // if null create new object
                          SentenceMethod cSentDispoStat = !lSentDispoStatArrayList?.isEmpty() ? lSentDispoStatArrayList.last(): new SentenceMethod();
							logger(((lSentDispoStatArrayList.empty) ? "Adding new" : "Updating existing") + " probation dispo status component");
							cSentDispoStat.sentenceMethodType = 'DISPO';
							cSentDispoStat.status = sDispoStatId;
							cSentDispoStat.statusDate = dDispoStatusDate;
							cSentDispoStat.sentence = cSentEvtProb;
							if (lSentDispoStatArrayList.empty)
								cSentEvtProb.sentenceMethods.add(cSentDispoStat);

							logger("Sentence dispo[$ds] status($sDispoStatId) - date($dDispoStatusDate)");
						}
					}

					// Only conditions to first CAProbationComponent
                    if (sp == 0) { // first CAProbationComponent section

                        // Add probation sentence conditions
                        for (int sc = 0; sc < oSentEvent.Sentence.Additional.CAProbationComponent.Conditions.Condition.size(); sc++) {
                            Object oProbCondSection = oSentEvent.Sentence.Additional.CAProbationComponent.Conditions.Condition[sc];
                            // set pointer to tag element

                            // Validate Advise Probation type
							String sCAProbationComponentType = (String)oProbCondSection.Type.@Word;
		                    if (validateLookupCode(sCAProbationComponentType, "SENTENCE_CONDITION_TYPE") == null)
                                this.aErrorList_.add(new ValidationError(false, cDefParty.case.caseNumber, logger("Sentence.Additional.CAProbationComponent[$sc].Type($sCAProbationComponentType'}) not found in LookupList[SENTENCE_CONDITION_TYPE]")));

                            List<SentenceCondition> lSentEvtCond = cSentEvtProb.collect("conditions[sentenceConditionType==#p1]", sCAProbationComponentType);
                            SentenceCondition cSentEvtCond = lSentEvtCond.last() ?: new SentenceCondition();
                            // if null create new object
                            logger(((!lSentEvtCond) ? "Adding new" : "Updating existing") + " CAProbation [${sc+1} of ${oSentEvent.Sentence.Additional.CAProbationComponent.Conditions.Condition.size()}] probation sentenceConditions(s) - Type($sCAProbationComponentType)");

                            cSentEvtCond.sentenceConditionType = sCAProbationComponentType;
                            //cSentEvtCond.memo = oProbCondSection.Comment ?: "";
                          	cSentEvtCond.message = oProbCondSection.Comment ?: "";
							cSentEvtCond.sentenceConditionBeginDate = convDateStrToDate((String)oProbCondSection.ConditionEffectiveDate.text(), "MM/dd/yyyy");

                            logger("CAProbationComponent[$sp] Sentence Condition - Type(${cSentEvtCond.sentenceConditionType}), Date(${cSentEvtCond.sentenceConditionBeginDate})");

                            // Add the latest condition status based on most recent date
							logger("Searching Condition Statuses for latest status");
							List<Object> lMaxDateStatus = oProbCondSection.ConditionStatuses.ConditionStatus.findAll { v -> v.StatusDate != null }.collect{it}; // get list of statuses to sort
							Object oMaxDateStatus = lMaxDateStatus.max { a, b -> Date.parse('MM/dd/yyyy', a.StatusDate.text()) <=> Date.parse('MM/dd/yyyy', b.StatusDate.text()) }; // get object w/ max date
                            if ( oMaxDateStatus != null ) {
								logger("Out of ${lMaxDateStatus.size()} Condition Statuse(s), found latest ${oMaxDateStatus.StatusDate.text()}");
                                cSentEvtCond.status = oMaxDateStatus.Status?.@'Word';
                                cSentEvtCond.statusDate = convDateStrToDate((String) oMaxDateStatus.StatusDate.text(), "MM/dd/yyyy");
                            } else
                                logger "No valid CAProbationComponent conditionStatuses found"

                            cSentEvtCond.setAssociatedSentence(cSentEvtProb);
							cSentEvtProb.status = "SUP";
                            // Add new condition
                            if (lSentEvtCond.empty) // new entry
                                cSentEvtProb.conditions.add(cSentEvtCond);
                        }
                    }
                    cSentEvtProb.setAssociatedCharge(cChg);

                    // Add new CAConfinementComponent to sentences
                    if (lSentEvtProb.empty)   // new entry
                  cChg.sentences.add(cSentEvtProb); logger("new Sentence: 1949: saved ${cSentEvtProb}");
                }
            } else
                logger("No valid CAConfinementComponent charge sentence found");

        } catch (Exception ex) {
            logger iTracking_.setException(ex.message, "Exception::addCAProbationComponent -  insert error");
            iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
        }

        return cDefParty;
    }

	/** -----------------------------------------------------------------------------------------
	 * Get person based indentification information
	 * @param sPerName - person name
	 * @param sPerType - person type
	 * @param sPerSubType - person subType
	 * @param sIdType - identificationType
	 * @param sIdNbr - identificationNumber
	 * @param bIsCreateNew - create new person if doesn't exist
	 * @returns person or null/exception if error
	 */
	public Person getPerson( String sPerName, String sPerType, String sPerSubType, String sIdType, String sIdNbr, boolean bIsCreateNew=true ) {
		Person cPerson = null;

		// Validate indentification inputs
		if (StringUtil.isNullOrEmpty(sIdType) || StringUtil.isNullOrEmpty(sIdNbr))
			return null;

		try {
			// Find associated person based on identification type/code
			logger("Searching for person - IdType($sIdType) | IdNbr($sIdNbr)");
			Where w = new Where();
			w.addEquals('identifications.identificationType', sIdType);
			w.addEquals('identifications.identificationNumber', sIdNbr);
			cPerson = DomainObject.find(Person.class, w, maxResult(1))[0];
			if (cPerson == null && bIsCreateNew) { // add person?
				logger("Person not found, adding new person ($sPerName)");

				// Add person
				cPerson = new Person();
				cPerson.personType = sPerType;
				cPerson.personSubType = sPerSubType;

				// Parse/Add last/first name
				PersonNameObj cPersonName = new PersonNameObj(sPerName);
				if (cPersonName.isValid()) { // valid name?
					cPerson.firstName = cPersonName.getFirstName();
					cPerson.lastName = cPersonName.getLastName();
				} else
					this.aErrorList_.add(new ValidationError(false, cSubCase.case.caseNumber, logger("New '$sIdType' person type added w/ idNbr($sIdNbr), needs name")));

				// Add identification
				Identification cId = new Identification();
				cId.identificationType = sIdType;
				cId.identificationNumber = sIdNbr;
				cId.setAssociatedPerson(cPerson);
				cPerson.identifications.add(cId);
				cPerson.saveOrUpdate();
			}

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getPerson - get person error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return cPerson;
	}

    /**
	 * Get case scheduledEvent assignment based identification person and assignment type/code.
	 * @param oCourtResource - Court resource ref
	 * @param cSchEvent - Pointer to active scheduled event
	 * @param cSubCase - Defendant case
	 * @param sAssignType - Assignment identification type
	 * @param sAssignCode - Assignment identification code
	 * @returns - current scheduled event
	 */
	 public ScheduledEvent getCaseEventAssignment(Object oCourtResource, ScheduledEvent cSchEvent, SubCase cSubCase, String sAssignType, String sAssignCode ) {
		Map<String,String> mPersonAssignMap = ['JUD': 'ODSYJUDGE', 'CR': 'ODSYRPTR']; // xml to ePros types

		// Validate assignment inputs
		if( cSchEvent == null || StringUtil.isNullOrEmpty(sAssignType) || StringUtil.isNullOrEmpty(sAssignCode) )  // invalid assignment type/code?
			return cSchEvent;

		try {
			if (mPersonAssignMap.any{it.key == sAssignType} ) { // allowable assignment to insert?

				// Map IP assignType to various ePros types and a validate it
				String sIdentNbr= sAssignCode;
				String sPartSubType = validateLookupCode(sAssignType, "PARTICIPANT_SUBTYPE");
				if( StringUtil.isNullOrEmpty(sPartSubType) )
					this.aErrorList_.add(new ValidationError(false, cSubCase.case.caseNumber, logger("Ce_Participant subType($sAssignType}) not found in [PARTICIPANT_SUBTYPE] lookup list") ));
				String sIdentType = validateLookupCode(mPersonAssignMap.get(sAssignType), "IDENTIFICATION_TYPE");
				if ( StringUtil.isNullOrEmpty(sIdentType) )
					this.aErrorList_.add(new ValidationError(false, cSubCase.case.caseNumber, logger("Indentification Type(${mPersonAssignMap.get(sAssignType)}) not found in [IDENTIFICATION_TYPE] lookup list") ));

				// Find/Create associated person based on identification type/code
				Person cPerson = null;
				if( (cPerson=getPerson((String)oCourtResource.Code.text(),"COURT",sAssignType,sIdentType,sIdentNbr)) == null ){
					this.aErrorList_.add(new ValidationError(false, cSubCase.case.caseNumber, logger("Error finding '$sIdentType' person type w/ idNbr($sIdentNbr)")));
					return null;
				}

				// If valid person find/create participant
				logger("Found participant person [${cPerson?.firstName}, ${cPerson?.lastName}], search for case participant type($sPartSubType)");

				// Search for valid Ce_Participant, if not available create new participant
				Ce_Participant cParticipant = cSubCase.case.collect("ce_Participants[type=='COURT' and subType==#p1 and status=='ACT']",sPartSubType).find{ Ce_Participant a ->
							   !a.person.collect("identifications[identificationType==#p1 and identificationNumber==#p2]",sIdentType,sIdentNbr).empty };
                  
                  cParticipant = cParticipant == null ? cSubCase.case.collect("ce_Participants[type=='COURT' && person == #p1]", cPerson).first() : cParticipant;
				if( cParticipant == null ) {
					logger("Not found, adding new case participant subType($sPartSubType)");

					// Create new participant
					cParticipant = new Ce_Participant();
					cParticipant.type = 'COURT';
					cParticipant.subType = sPartSubType;
					cParticipant.status = 'ACT';
					cParticipant.statusDate = new Date();

					// Add person and case references
					cParticipant.person = cPerson;
					cParticipant.case= cSubCase.case;
					cParticipant.subCase = cSubCase;

					// Add/Save new participant
					cSubCase.case.ce_Participants.add(cParticipant); // add collection
					cParticipant.saveOrUpdate();

					// Add the scheduledEvent(LeftObj) cross ref to correct Ce_Participant(RightObj) type
                  if (cSchEvent.collect("xrefs[entityType=='Ce_Participant' and refType=='REFERS_TO'].ref[id == #p1]", cParticipant.id).isEmpty()){
					cSchEvent.addCrossReference(cParticipant,"REFERS_TO");
                  }

                } else{ // found
					logger("Case assignment role [$sAssignType] found");
                  
                  if(cSchEvent.collect("xrefs[entityType=='Ce_Participant' and refType=='REFERS_TO'].ref[id == #p1]", cParticipant.id).isEmpty()){
                    cSchEvent.addCrossReference(cParticipant,"REFERS_TO");
                    cSchEvent.saveOrUpdate();
                  }
                }
			} else
				logger("Case assignment($sAssignType) role not supported");

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getCaseEventAssignment - Case assignment error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return cSchEvent;
	}

	/** ------------------------------------------------------------------------------------------------------
	 * Search first for BAR# to find attorney, if not found add attorney with Bar# using CasePartyName
	 * info using firstName, middleName and lastName, if CasePartyName tag doesn't exist use Description
	 * and add as the organizationName. Use this Person record to populate the Person entity in the
	 * ce_Participant mapping.
	 * @param xmlIPSluper - xml tree structure
	 * @param sAttorneyID - Used to find Attorney node in xml
	 * @returns a person if search/add successful, else null if xml Attorney person not found.
	 */
	public Person getAttyEProsPerson(Object xmlIPSluper, String sAttorneyType, String sAttorneyID ) {
		if (!xmlIPSluper || !sAttorneyID) return null;
		Person cPerson= null;

		try {
			// Search for ePros person
			for (int x = 0; x < xmlIPSluper.Case.Attorney.size(); x++) {
				if (xmlIPSluper.Case.Attorney[x].@"ID" == sAttorneyID) {    // valid ID?

					// Search by Bar#
					if ( !StringUtil.isNullOrEmpty(xmlIPSluper.Case.Attorney[x].BarNumber.text())) {    // valid bar#
						logger("Searching ePros AttyID(${sAttorneyID}) Person by Bar#(${xmlIPSluper.Case.Attorney[x].BarNumber.text()})")

						Where w = new Where();
						if( xmlIPSluper.Case.Attorney[x].CasePartyName.size() ) // add lastName into search?
							w.addEquals('lastName', (String)xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text());
						w.addEquals('identifications.identificationType', 'BAR');
						w.addEquals('identifications.identificationNumber', (String) xmlIPSluper.Case.Attorney[x].BarNumber.text());
						cPerson = DomainObject.find(Person.class, w, maxResult(1))[0];
						logger("Atty Bar#(${xmlIPSluper.Case.Attorney[x].BarNumber.text()}), " + ((cPerson != null)?"found":"not found") );
					}

					// Search by first/last name if valid CasePartyName tag and valid lastName
					if( cPerson == null && xmlIPSluper.Case.Attorney[x].CasePartyName.size() &&
						!StringUtil.isNullOrEmpty((String)xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text()) ) { // valid tag?
						logger("Searching ePros AttyID(${sAttorneyID}) by First/Last name(${xmlIPSluper.Case.Attorney[x].CasePartyName.NameFirst.text()}, " +
								"${xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text()})");

						Where w1 = new Where().addEquals('personType', 'STAFF');
						w1.addEquals('lastName', (String)xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text());
						if ( !StringUtil.isNullOrEmpty(xmlIPSluper.Case.Attorney[x].CasePartyName.NameMiddle.text()) )
							w1.addEquals('middleName', (String) xmlIPSluper.Case.Attorney[x].CasePartyName.NameMiddle.text());
						w1.addEquals('firstName', (String)xmlIPSluper.Case.Attorney[x].CasePartyName.NameFirst.text());
						cPerson = DomainObject.find(Person.class, w1, maxResult(1))[0];
						logger("Atty(STAFF) - First/LastName(${xmlIPSluper.Case.Attorney[x].CasePartyName.NameFirst.text()}, " +
								"${xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text()}), " + ((cPerson != null) ? "found" : "not found"));
					} else
						logger(" No valid CasePartyName.NameLast, search by name skipped")

					// Add new atty person to ePros if no valid person found, but w/ a valid CasePartyName
					if( cPerson == null && xmlIPSluper.Case.Attorney[x].CasePartyName.size() ) {
						if( !StringUtil.isNullOrEmpty(xmlIPSluper.Case.Attorney[x].BarNumber.text())
							&& !StringUtil.isNullOrEmpty(xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text()) ) { // must have?
							logger("Adding Atty($sAttorneyType) person - Bar#(${xmlIPSluper.Case.Attorney[x].BarNumber.text()}, Name(${xmlIPSluper.Case.Attorney[x].CasePartyName.NameFirst.text()}, " +
									"${xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text()})");

							// Add person
							cPerson = new Person();
							cPerson.personType= "REP";
							cPerson.firstName = xmlIPSluper.Case.Attorney[x].CasePartyName.NameFirst.text();
							cPerson.middleName= xmlIPSluper.Case.Attorney[x].CasePartyName.NameMiddle.text();
							cPerson.lastName = xmlIPSluper.Case.Attorney[x].CasePartyName.NameLast.text();

							// Add person/identification
							Identification cId = new Identification();
							cId.identificationType = 'BAR';
							cId.identificationNumber = xmlIPSluper.Case.Attorney[x].BarNumber.text();
							cId.setAssociatedPerson(cPerson);
							cPerson.identifications.add(cId);

							// Update person entity w/ new person reference
							cPerson.saveOrUpdate();
						} else
							logger("Attorney person cannot be added, no matching Bar# or lastName");

						// No valid person and no CasePartyName tag, create person based on organizationName
					} else if( cPerson == null && !xmlIPSluper.Case.Attorney[x].CasePartyName ) { // valid tag?
						if( !StringUtil.isNullOrEmpty(xmlIPSluper.Case.Attorney[x].Connection.Description.text()) ) {
							logger("No Bar# or CasePartyName tag found, adding Atty Person w/ Orig(${xmlIPSluper.Case.Attorney[x].Connection.Description.text()}))")

							// Search for existing organization
							Where w = new Where();
							w.addEquals('personType', 'ORGANIZATION');
							w.addEquals('organizationName', (String)xmlIPSluper.Case.Attorney[x].Connection.Description.text());
							cPerson = DomainObject.find(Person.class, w, maxResult(1))[0];
							if( cPerson == null ) { // new?
								cPerson = new Person();
								cPerson.personType = "ORGANIZATION";
								cPerson.organizationName = xmlIPSluper.Case.Attorney[x].Connection.Description.text();

								// Update person entity w/ new person reference
								cPerson.saveOrUpdate();

							} else
								logger("Organization(${xmlIPSluper.Case.Attorney[x].Connection.Description.text()}) already exists");
						} else
							logger("Organization person cannot be added, invalid connection description");
					}

					return cPerson; // mission complete!
				}
			}
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getAttyEProsPerson - Error searching attorney person");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return null;
	}

	/** ------------------------------------------------------------------------------------
	 * Search ePros for valid case based on odyssey caseType/caseNumber.otherCaseNumber criteria.
	 *
	 * 1.  Look to the Odyssey CaseType in the message and if it's a "P" or "R" proceed with the following.
	 * 	   If not skip down to section 2.
	 *
	 *     Look to the Odyssey message and see if there is an Underlying CaseNumber (Case/CaseCrossReference/
	 *     CrossCaseNumber where Case/CaseCrossReference/CaseCrossReferenceType = 'UCN'). If there is, try
	 *     to match it to a CRT OtherCaseNumber in ePros (Case.otherCaseNumbers[ type = 'CRT' ]).
	 *     If not found proceed to next step.
	 *
	 * 	   Look to the Odyssey CaseNumber (Case/CaseNumber) and try to match it to an PRCS or PAROLE OtherCaseNumber
	 * 	   in ePros (Case.otherCaseNumberstype in ( 'PRCS', 'PAROLE'). If still not found proceed to next step.
	 *
	 *     Look to the Odyssey message and see if there is an DA Case Number (Case/CaseCrossReference/CrossCaseNumber
	 *     where Case/CaseCrossReference/CaseCrossReferenceType = 'DA'). If there is, try to match it to a caseNumber
	 *     in ePros (Case.caseNumber). If not found proceed to next step.
	 *
	 *     At this point, the case could not be matched so an error should be thrown. Because we don't have a case
	 *     to tie a message to and put into the error queue, we will have to decide on how that error will be provided
	 *     to the customer.
	 *
	 * 2. Assuming the Odyssey CaseType in the message is *not *a "P" or "R", proceed with the following:
	 *
	 *    Look to the Odyssey CaseNumber (Case/CaseNumber) and try to match it to a CRT OtherCaseNumber in ePros
	 *    (Case.otherCaseNumberstype='CRT'). If still not found proceed to next step.
	 *
	 *    Look to the Odyssey message and see if there is an DA Case Number (Case/CaseCrossReference/CrossCaseNumber
	 *    where Case/CaseCrossReference/CaseCrossReferenceType = 'DA'). If there is, try to match it to a caseNumber
	 *    in ePros (Case.caseNumber). If not found proceed to next step.
	 *
	 *    At this point, it will return an error that the case wasn't found.
	 *
	 * @param XmlIPSluper - pointer to xml xml schema
	 * @param fFilePath - used for error reporting
	 * Returns - case for court case if found. Otherwise returns null
	 */
	public Case getCase( Object xmlIPSluper, File fFilePath ) {

		// Odyssey case types
		Case cCase = null;

		try {
            Object xmlCase = xmlIPSluper.Case;

            logger("Search for ePros Court Case");

            // Get Odyssey caseType information for search
            if (!xmlCase.CaseType.size()) {
                this.aErrorList_.add(new ValidationError(true, "", fFilePath?.getName(), logger("No Odyssey caseType section found in IP message")));
                return null;
            }
            String sCaseType = xmlCase.CaseType;
            String sCaseTypeWord = xmlCase.CaseType.@Word;
            // 1. Search for UCN special caseTypes
            String sUCNCaseType = (String) mOdyUnderlyingCaseTypes_.find { it.value == sCaseTypeWord }?.key;
            if (!StringUtil.isNullOrEmpty(sUCNCaseType)) { // special case found?
                // Search for valid UCN special cases w/ CaseType = 'P' (PRCS) or 'R' (PAROLE)
                Object cCaseCrossRef = xmlCase.CaseCrossReference.find { node -> node.CaseCrossReferenceType.@'Word' == 'UCN' };
                // get node
                if (cCaseCrossRef && cCaseCrossRef.CrossCaseNumber.size()) {    // underlying case type?
					logger("UCN caseType($sUCNCaseType) found");
                    logger("Query ePros case using UCN caseCrossRef#(${cCaseCrossRef.CrossCaseNumber.text()}) w/ ePros otherCaseType(CRT)");
                    // 1.1 Search for otherCaseNumber using type 'CRT' w/ Odyssey caseCrossRef.CrossCaseNumber
                    RichList caseNumbers = new RichList();
                    caseNumbers.addAll(cCaseCrossRef.CrossCaseNumber.text().split("[;,]"));
                  	Where w = new Where();
                    w.addEquals('otherCaseNumbers.type', 'CRT');
                    w.addContainsAny("otherCaseNumbers.number", caseNumbers);
                    //cCase = DomainObject.find(Case.class, w, maxResult(1))[0];
                  cCase = DomainObject.find(Case.class, w).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
                  if (cCase != null){
						logger("Found <b>eProsecutor Case DA#${cCase.caseNumber}</b> Case ID:${cCase.id} assoc. w/ UNC to otherCaseType(CRT)");
                  }
                    // 1.2 Search for case using odyssey case.CaseNumber to ePros otherCaseNumber/type ( 'PRCS', 'PAROLE')
                    if (cCase == null && xmlCase.CaseNumber.size()) { // valid ody case#
                        logger("Query ePros case using Odsy case.CaseNbr#(${xmlCase.CaseNumber.text()}) w/ ePros otherCaseType($sUCNCaseType)");

                        w = new Where().addEquals('otherCaseNumbers.type', sUCNCaseType);
                        w.addEquals('otherCaseNumbers.number', xmlCase.CaseNumber.text());
                        //cCase = DomainObject.find(Case.class, w, maxResult(1))[0];
                      cCase = DomainObject.find(Case.class, w).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
                      if (cCase == null){
                        RichList xmlCaseNumbers = new RichList();
                        xmlCaseNumbers.addAll(xmlCase.CaseNumber.text().toString().split("[;,]"));
                        Where where = new Where();
                        where.addEquals('otherCaseNumbers.type', sUCNCaseType);
                        where.addContainsAny("otherCaseNumbers.number", xmlCaseNumbers);
                        cCase = DomainObject.find(Case.class, where).isEmpty() ? null : DomainObject.find(Case.class, where).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
                      }
                      if (cCase != null){
                        //logger("Found DA#($cCase.caseNumber) Case ID:${cCase.id} assoc. w/ otherCaseType($sUCNCaseType)");
                        logger("Found <b>eProsecutor Case DA#${cCase.caseNumber}</b> Case ID:${cCase.id} assoc. w/ otherCaseType($sUCNCaseType)");
                      }
                    }

                    // 1.3 Search for case using odyssey DA CaseCrossReference number referenced to Case.caseNumber
                    cCaseCrossRef = xmlCase.CaseCrossReference.find { node -> node.CaseCrossReferenceType.@'Word' == 'DA' }; // get node
                    if (cCase == null && cCaseCrossRef.CrossCaseNumber.size()) { // DA case type?
                        logger("Query ePros case using DA caseCrossReference#(${cCaseCrossRef.CrossCaseNumber.text()}) to ePros case.caseNumber");

                        // Search for case.caseNumber match w/ DA CaseCrossReferenceNumber
                      	String ipDANumber = cCaseCrossRef.CrossCaseNumber.text();
                      	ipDANumber = ipDANumber.contains(" ") == true ? ipDANumber.split(" ")[0] : ipDANumber;
                        //cCase = DomainObject.find(Case.class, "=caseNumber", ipDANumber)[0];
                      cCase = DomainObject.find(Case.class, "=caseNumber", ipDANumber).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
                        if (cCase != null)
                            logger("Found case#($cCase.caseNumber) assoc. w/ DA CaseCrossReference number");
                    }

                    // 1.4 If case found, check ePros case.otherCaseNumbers for current odyssey caseNumber and
                    // crossRefType match, it will be added if not found.
                    if (cCase != null && xmlCase.CaseNumber.size()) { // found?
                        logger("Check case.otherCaseNumbers for matching case#(${xmlCase.CaseNumber.text()}) and type(${sUCNCaseType})");

                        OtherCaseNumber cOthCaseNbr = cCase.collect("otherCaseNumbers[type==#p1 and number==#p2]", sUCNCaseType,(String)xmlCase.CaseNumber.text()).last();
                        
                        if ( cOthCaseNbr == null ) { // new?
                            logger("Not found, adding case.otherCaseNumber#(${xmlCase.CaseNumber.text()}) and type(${sUCNCaseType})");

                            // Validate the caseType w/ lookup list
                          if (validateLookupCode(sUCNCaseType, "OTHER_NUMBER_TYPE") == null){
                                this.aErrorList_.add(new ValidationError(false, cCase.caseNumber, logger("Odyssey UCN caseType(${sUCNCaseType}) not found in LookupList[OTHER_NUMBER_TYPE]")));
                          }
                          		// Add new OtherCaseNumber
                            	/*cOthCaseNbr= new OtherCaseNumber();
                            	cOthCaseNbr.type = sUCNCaseType;
                            	cOthCaseNbr.number = xmlCase.CaseNumber.text();
                            	cOthCaseNbr.case = cCase;                          
                            	cCase.otherCaseNumbers.add(cOthCaseNbr);
                            	cCase.saveOrUpdate();*/
                          

                        } else{
                            logger("Found case.otherCaseNumbers.type($sUCNCaseType)");
                        }
                    } else { // case not found!
                        this.aErrorList_.add(new ValidationError(true, "", fFilePath?.getName(), logger("No ePros case found for odyssey UCN caseType($sUCNCaseType)")));
                        return null;
                    }

                } else if (cCase == null && ["P","R"].contains(sCaseTypeWord)){
					RichList xmlCaseNumbers = new RichList();
                        xmlCaseNumbers.addAll(xmlCase.CaseNumber.text().toString().split("[;,]"));
                        Where where = new Where();
                        where.addContainsAny('otherCaseNumbers.type', ["P","R"]);
                        where.addContainsAny("otherCaseNumbers.number", xmlCaseNumbers);
                        cCase = DomainObject.find(Case.class, where).isEmpty() ? null : DomainObject.find(Case.class, where).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
				} else {
                    this.aErrorList_.add(new ValidationError(true, "", fFilePath?.getName(), logger("Case search error, odyssey caseType($sUCNCaseType) found w/ no UCN caseCrossReference section")));
                    return null;
                }

            } else {    // all other case types
                // 2. Search for basic caseTypes
                if (xmlCase.CaseNumber.size()) { // odyssey case#?
                    logger("Query case using Court#(${xmlCase.CaseNumber.text()}) w/ otherCaseType(CRT)");

                    // 2.1 Search for ePros case using Odyssey caseNumber using otherCaseNumbers w/ type(CRT)
                    Where w = new Where().addEquals('otherCaseNumbers.type', 'CRT');
                    w.addEquals('otherCaseNumbers.number', xmlCase.CaseNumber.text());
                    //cCase = DomainObject.find(Case.class, w, maxResult(1))[0];
                  cCase = DomainObject.find(Case.class, w).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
                    if (cCase != null && cCase.getClass() == Case){
                      
                    }
                    if (cCase != null)
                        //logger("Found case#($cCase.caseNumber) assoc. w/ otherCaseType(CRT)");
						logger("Found <b>eProsecutor Case DA#${cCase.caseNumber}</b> Case ID:${cCase.id} assoc. w/ otherCaseType(CRT)");
                    // 2.2 Search for ePros case using odyssey DA CaseCrossReference number referenced to Case.caseNumber
                    Object cCaseCrossRef = xmlCase.CaseCrossReference.find { node -> node.CaseCrossReferenceType.@'Word' == 'DA' };  // get node
                    if (cCase == null && cCaseCrossRef.CrossCaseNumber.size()) { // DA case type?
                        logger("Query ePros case using DA caseCrossReference#(${cCaseCrossRef.CrossCaseNumber.text()}) to ePros case.caseNumber");

                        // Search for case.caseNumber match w/ DA CaseCrossReferenceNumber
                      	String ipDANumber2 = cCaseCrossRef.CrossCaseNumber.text();
                      	ipDANumber2 = ipDANumber2.contains(" ") == true ? ipDANumber2.split(" ")[0] : ipDANumber2;
                        //cCase = DomainObject.find(Case.class, "=caseNumber", ipDANumber2)[0];
                      cCase = DomainObject.find(Case.class, "=caseNumber", ipDANumber2).sort({a,b -> a.id <=> b.id}).find({it -> it.id != null});
                        if (cCase != null)
                            logger("Found case#($cCase.caseNumber) assoc. w/ DA CaseCrossReference number");
                    }
                }

                // Test case error and report it
                if (cCase == null) { // valid?
                    this.aErrorList_.add(new ValidationError(true, "", fFilePath?.getName(), logger("No ePros case found with odyssey caseNumbers")));
                    return null;
                }
            }
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getCase - Court case search error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return cCase;
	}

	/** ---------------------------------------------------------------------------------
	 * Search string for input value based on delimiter
	 * @param sStr
	 * @param sVal
	 * @param sDelimiter
	 * @returns valid string if found or null if not
	 */
	public String searchStrForToken(String sStr, String sVal, String sDelimiter) {
		if( StringUtil.isNullOrEmpty(sStr) || StringUtil.isNullOrEmpty(sVal) )
			return null;
		String[] aTokens = sStr.split(sDelimiter);
		for( String s in aTokens ) {
			if( s.contains(sVal.trim()) )
				return s;
		}
		return null;
	}

	/** -------------------------------------------------------------------------------------------------------------
	 * Search for a valid party based on xmlPartyType and methods described below.
	 *
	 *  1.	Match on Odyssey Person ID
	 *	    The XML file will contain an InternalNameID value within the CasePartyName section of the XML that
	 *      contains a Connection node
	 *
	 *      An attempt should be made to see if this value matches any Identification records of identificationType =
	 *      ODPERSID on any of the defendant Person records or their AKA records.
	 *
	 *  2.	Match on driverÃ¢â‚¬â„¢s license (Defendant only search)
	 *      If the Odyssey Person ID cannot be matched and there is a drivers license in the source file, an attempt
	 *      should be made to match the defendant or any of their AKAÃ¢â‚¬â„¢s by driverÃ¢â‚¬â„¢s license.
	 *
	 *  3.	Match on first/middle/last name or Business name.
	 *
	 *  4.  If no person found creat party/person if and Plaintiff search, the create a plaintiff party
	 *
	 * @param sCaseNumber - Class:String - Case number
	 * @param xmlIPSluper - Class:GPathResult - Xml parsed elements
	 * @param xmlPartyType - Class:Party - Xml party type
	 * @param bCreateNewPerson - Class:Boolean - (true= Create person if doesn't exist, false=disabled)
	 * @returns - valid casePartyObj if found, else null if exception/error
	 */
	public CasePartyObj getCaseParty( Case cCase, Object xmlIPSluper, String xmlPartyType, boolean bCreateNewPerson= false ) {
		if ( cCase == null || StringUtil.isNullOrEmpty(xmlPartyType) )  // no case or partyType?
			return null;

		// Create translation map for xml to eSuite party types
		Map<String, String> mPartyXmlToeProsMap = new HashMap<String, String>() {{
			put(XML_DEFENDANT_PARTY_TYPE_,'DEF'); put(XML_PLAINTIFF_PARTY_TYPE_,'PLT');
		}};

		try {
			logger "Searching for case party using IP($xmlPartyType) to ePros(${mPartyXmlToeProsMap.get(xmlPartyType)})";
			String sOfsPerId = null;
			Object oCasePty = null;

			// Search for caseParty type then search ePros for matching [ODPERSID] person id type
			if( (oCasePty=xmlIPSluper.Case.CaseParty.find{ n -> n.Connection.@'Word' == xmlPartyType }) ) {	// get caseParty valid?

				// Get caseParty person ID to associate w/ ePros party.person
				sOfsPerId = oCasePty.CasePartyName.find{ n -> (String) n.@'Current' == 'true' }?.@'InternalNameID'; // association personID

				// Search method 1/3 to identify party using InternalNameID and ePros ODPERSID
				if (!StringUtil.isNullOrEmpty(sOfsPerId)) {  // valid personId?
					logger "Found IP ${xmlPartyType} caseParty(${oCasePty.@'ID'}) InternalNameID($sOfsPerId) to associate w/ ePros ${mPartyXmlToeProsMap.get(xmlPartyType)} party";

					// Search person.identification for 'ODPERSID' identifier party
					logger "Searching [${mPartyXmlToeProsMap.get(xmlPartyType)}] party.person.identification for [ODPERSID]";
					for (Party cPty in cCase.collect("subCases.parties[partyType==#p1]", mPartyXmlToeProsMap.get(xmlPartyType))) {
						if (cPty.collect("person.identifications[identificationType=='ODPERSID' and identificationNumber==#p1]", sOfsPerId)) {
							logger "Found partyId(${cPty.id}) w/ person(${cPty.lastName}, ${cPty.firstName} at <b>eProsecutor Case DA#${cPty.case.caseNumber}</b> Case ID:${cPty.case.id})";
							return new CasePartyObj(cPty, oCasePty);
						}
					}

					// Search personAKA.identification 'ODPERSID' identifier party
					logger "Searching [${mPartyXmlToeProsMap.get(xmlPartyType)}] party.person.personAKAs.identifications for [ODPERSID]"
					for (Party cPty in cCase.collect("subCases.parties[partyType==#p1]", mPartyXmlToeProsMap.get(xmlPartyType))) {
						if (cPty.collect("person.personAKAs.associatedPerson.identifications[identificationType=='ODPERSID' and identificationNumber==#p1]", sOfsPerId)) {
							logger "Found partyId(${cPty.id}) w/ person(${cPty.lastName}, ${cPty.firstName})";
							return new CasePartyObj(cPty, oCasePty);
						}
					}
				}

				// Search method 2/3 to identify party using DL/DOB/SSN/Name on OFS and ePros
				if (xmlPartyType == XML_DEFENDANT_PARTY_TYPE_) { // defendant only?
					String sDLNbr = oCasePty.DriversLicense.DriversLicenseNumber.text()
					if (sDLNbr) { // valid?
						logger "Searching ${mPartyXmlToeProsMap.get(xmlPartyType)}] party.person by Driver's License($sDLNbr)";
						for (Party cPty in cCase.collect("subCases.parties[partyType==#p1]", mPartyXmlToeProsMap.get(xmlPartyType))) {
							if (cPty.collect("person.identifications[identificationType=='CDL' and identificationNumber==#p1]", sDLNbr)) {
								logger "Found partyId(${cPty.id}) w/ person(${cPty.lastName}, ${cPty.firstName})";
								updatePartyPersonWithODPERSID(cPty, sOfsPerId); // update person identifier
								return new CasePartyObj(cPty, oCasePty);
							}
						}

						// Search personAKA.identification for matching DL identifier for party match
						logger "Searching ${mPartyXmlToeProsMap.get(xmlPartyType)}] party.personAKAs by Driver's License($sDLNbr)"
						for (Party cPty in cCase.collect("subCases.parties[partyType==#p1]", mPartyXmlToeProsMap.get(xmlPartyType))) {
							if (cPty.collect("person.personAKAs.associatedPerson.identifications[identificationType=='CDL' and identificationNumber==#p1]", sDLNbr)) {
								logger "Found partyId(${cPty.id}) w/ person(${cPty.lastName}, ${cPty.firstName})";
								updatePartyPersonWithODPERSID(cPty, sOfsPerId); // update person identifier
								return new CasePartyObj(cPty, oCasePty);
							}
						}
					} else
						logger "No drivers license number found, DL search skipped";
				}

				// Search method 3/3 Search for matching party based on person first/middle/last or business name
				logger "Searching [${mPartyXmlToeProsMap.get(xmlPartyType)}] party.person name first(${oCasePty.NameFirst.text()})/middle(${oCasePty.NameMiddle.text()})/last(${oCasePty.NameLast.text()}) or business(${oCasePty.FormattedName.text()})";

				// Search for existing organization
				boolean bIsBusiness = (oCasePty.NameType.text() == 'Business');
				Where w = new Where();
				w.addEquals('case.caseNumber', cCase.caseNumber);
				w.addEquals('partyType', mPartyXmlToeProsMap.get(xmlPartyType));
				if (!StringUtil.isNullOrEmpty(oCasePty.NameFirst.text()) && !bIsBusiness)  // valid firstName and not Biz?
					w.addEquals('person.firstName', (String) oCasePty.NameFirst.text());
				if (!StringUtil.isNullOrEmpty(oCasePty.NameMiddle.text()) && !bIsBusiness) // valid middleName and not Biz?
					w.addEquals('person.middleName', (String) oCasePty.NameMiddle.text());
				if (bIsBusiness && !StringUtil.isNullOrEmpty(oCasePty.FormattedName.text())) // valid biz w/ formatted name?
					w.addEquals('person.lastName', (String) oCasePty.FormattedName.text());
				else  // use standard lastName
					w.addEquals('person.lastName', (String) oCasePty.NameLast.text());
				Party cPty = DomainObject.find(Party.class, w, maxResult(1))[0];
				if (cPty != null) {
					logger "Found partyId(${cPty.id}) w/ person(${cPty.lastName}, ${cPty.firstName}) via name search";
					updatePartyPersonWithODPERSID(cPty, sOfsPerId); // update person identifier
					return new CasePartyObj(cPty, oCasePty);
				}

				// Create plaintiff party person if not found and flag is enabled
				if (bCreateNewPerson && xmlPartyType == XML_PLAINTIFF_PARTY_TYPE_) { // create new plaintiff party
					Party cNewPty = new Party();
					cNewPty.partyType = mPartyXmlToeProsMap.get(xmlPartyType);

					bIsBusiness = (oCasePty.NameType.text() == 'Business');
					logger "Creating plaintiff party  - " + ((bIsBusiness) ? oCasePty.FormattedName.text() : "(${oCasePty.NameFirst.text()}, ${oCasePty.NameLast.text()})");

					// Create party.person for a standard or business person
					Person cPer = new Person();
					if (oCasePty.NameLast.size()) { // standard w/ at least a lastName
						if (bIsBusiness && !StringUtil.isNullOrEmpty(oCasePty.FormattedName.text())) {
							// business w/ formatted name?
							cPer.lastName = oCasePty.FormattedName.text();
						} else {    // standard or business w/ no formatted name
							cPer.lastName = oCasePty.NameLast.text();
							if (!StringUtil.isNullOrEmpty(oCasePty.NameMiddle.text()))
								cPer.middleName = oCasePty.NameMiddle.test();
							if (!StringUtil.isNullOrEmpty(oCasePty.NameFirst.text()))
								cPer.firstName = oCasePty.NameFirst.test();
						}

						// Assign person references to party/case
						cNewPty.person = cPer;
						cNewPty.case = cCase;
						cCase.parties.add(cNewPty);

						// Save new party
						cNewPty.saveOrUpdate();
						updatePartyPersonWithODPERSID(cNewPty, sOfsPerId); // update person identifier
						return new CasePartyObj(cNewPty, oCasePty);

					} else
						logger("Unable to create Plaintiff party for PlaintiffId($sOfsPerId), No lastName found");
				}
			}

		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::getCaseParty - [${mPartyXmlToeProsMap.get(xmlPartyType)}] party search error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}

		return null;
	}

	/** -----------------------------------------------------------------------------------
	 * Update/Add Def party person identification ID 'ODPERSID' w/ OFS xml InternalNameID.
	 * This is used to match ePros defendant parties w/ a def party in the IP xml file.
	 * If it doesn't exists, it will be added to person.identifications.
	 * @param cParty - Class:Party - Valid party
	 * @param sIPPtyPersonId - Class:String - IP Person identification ID
	 * @Returns - 0= Failed, 1=Success
	 */
	public boolean updatePartyPersonWithODPERSID(Party cParty, String sIPPtyPersonId) {
		if ( cParty == null || StringUtil.isNullOrEmpty(sIPPtyPersonId) )  // param error?
			return false;

		try {
			logger "Updating ePros party[${cParty.partyType}].person Id(ODPERSID) w/ IP xml partyId($sIPPtyPersonId)";

			// Update/add person identification 'ODPERSID'
			List<Identification> lId = cParty.collect("person.identifications[identificationType=='ODPERSID']");
			Identification cId = lId.last() ?: new Identification();
			cId.identificationType = 'ODPERSID';
			cId.identificationNumber = sIPPtyPersonId;
			cId.setAssociatedPerson(cParty.person);
			if (lId.empty)   // new entry?
				cParty.person.identifications.add(cId);

			cParty.saveOrUpdate();
			logger "Done";

			return true;
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::updatePartyPersonWithODPERSID - Person ODPERSID update error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
		}
		return false;
	}

	/** ----------------------------------------------------------------------------------------------
	 * Method to convert dollars to cents. The leading '$' and any ',' will be removed during the
	 * dollar conversion. Uses BigDecimal vs. double to achieve higher precision over a using
	 * fixed number of decimals.
	 * @param sUsdValue - Dollar value (e.g. $40, 40.00, 400, 4,000)
	 * @returns - value in cents or exception if > than 2 decimal precisions.
	 */
	public long usdToCents(String sUsdValue) {
		long lCents= 0;
		if (!StringUtil.isNullOrEmpty(sUsdValue)) { // amount?
			BigDecimal bdUsd = new BigDecimal(sUsdValue.replaceAll('[$,]', ""));
			if( bdUsd.scale() > 2 ) // > 2dp used?
				throw new Exception( "Exception::usdToCents - dollar input was > than 2 decimal places");
			lCents= bdUsd.movePointRight(2).longValueExact();
		}
		return lCents;
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
			return sRetDate;
		}
		catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::convDateFmtToStr - Date to String conversion error");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return null;
		}
	}

	/** ------------------------------------------------------------------------------------
	 * Function to convert date and time string into a Date object
	 * @param sDateTime - Date/Time string to convert to Date type
	 * @param sDateFormat - Date format to use (eg. "yyyyMMddHHmm")
	 * @Return - formatted Date object
	 */
	public Date convDateStrToDate(String sDateTime, String sFormat) {
		if( StringUtil.isNullOrEmpty(sDateTime) ) // empty?
			return null;

		// Convert dateTime string to selected date format
		try {
			DateFormat df = new SimpleDateFormat(sFormat);
			Date dRetDate = df.parse(sDateTime);
			logger(" Date($sDateTime) converted to $dRetDate");
			return dRetDate
		}
		catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::convDateStrToDate - Date to String conversion error");
			_eTtracking.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return null;
		}
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

	/** ----------------------------------------------------------------------------------------------
	 * Finalize case interface tracking detail by adding a log entry. If a reject is present, send
	 * eMail alert and reject the file. If successful, move file to processed folder.
	 * @param cTracking - Interface tracking
	 * @param fFilePath - Processed file
	 * @returns 0= failure, 1= successful
	 */
	public boolean finalizeCaseTrackingDetail( MyInterfaceTracking cTracking, File fFilePath ) {
		try {
			logger( "Finalizing case script tracking detail")
			// Get reject status
			boolean bIsReject = (aErrorList_.findAll { e -> e.bReject_ }.size() > 0)
			// Check error batch for reject and move file to correct output bin
			if( bIsReject || !StringUtil.isNullOrEmpty(iTracking_.tracking_.exception) ) {
				cTracking.updateResult(iTracking_.RESULT_FAIL_OFS_FAILED_);
				if (fFilePath) {    // valid source
                  String targetFailDirectory = this.aErrorList_.findAll({it -> it.sDesc_.contains("No ePros case found") || it.sDesc_ == "No ePros case found with odyssey caseNumbers"}).isEmpty() ? cRule_._inboundFailedSmbPath : cRule_._unmatchedDirectory;
					logger("Moving ${fFilePath.getName()} file to ${targetFailDirectory} folder");
                    //(fFilePath, (String) cRule_._inboundFailedSmbPath); // move to reject bin
                    org.apache.commons.io.FileUtils.moveFileToDirectory( fFilePath, new File(targetFailDirectory), false);
				}

				// Report failure via eMail
				sendEmail(cTracking,true);  // send case email report for reject errors

			} else {	// successful!
				cTracking.updateResult(iTracking_.RESULT_SUCCESS_);
				if (fFilePath) {   // valid source
					logger("Moving ${fFilePath.getName()} file to ${cRule_._inboundProcessedSmbPath} folder");
                    org.apache.commons.io.FileUtils.moveFileToDirectory( fFilePath, new File(cRule_._inboundProcessedSmbPath), false);
				}

				// Debug mode will send all success runs to email
				if( this.bDebug_ )   // debug?
					sendEmail(cTracking, true);
			}

			return true;
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::FinalizeCaseTrackingDetail - Error finalizing case tracking");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
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

	/** ----------------------------------------------------------------------------------------------
	 * Process case errors. If case is valid, assign errors to case tracking entity.
	 * @param cTracking - tracking for error
	 * @param fFilePath - Processed file
	 * @returns 0= failure, 1= successful
	 */
	public boolean processCaseErrors( MyInterfaceTracking cTracking, File fFilePath ) {
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
					sMemo.add("<b>OFS ${iTracking_.INTERFACE_TYPE_} Failed</b>");
				else
					sMemo.add("<b>OFS ${iTracking_.INTERFACE_TYPE_} Notice</b>");
				if (!StringUtil.isNullOrEmpty(e.sCaseNum_))  // valid case#?
					sMemo.add("Case#(${e.sCaseNum_})");
				sMemo.add(e.sDesc_);                                     // add error description on end

				// Configure eSuite assignment error type if set
				String sType = iTracking_.TRACKING_DETAIL_INT_REVIEWERR_;   // review warning error
				if (e.bReject_)	// rejected?
					sType = iTracking_.TRACKING_DETAIL_INT_REJECTERR_;      // reject error assignment

				// Add tracking detail message to trigger assignment queues if needed
				cTracking.addTrackingDetail(sType, iTracking_.TRACKING_STATUS_ERROR_, "Validation error", sMemo.toString() );

				// Update console output
				logger(iValErrs + 1 + ") " + sMemo);

				iValErrs++;
				e.bProcessed_ = true; // indicate error processed
			}

			return true;
		} catch (Exception ex) {
			logger iTracking_.setException(ex.message, "Exception::processErrors - Error process handler");
			iTracking_.updateResult(iTracking_.RESULT_FAIL_EXCEPTION_);
			return false;
		}
	}

	/** ----------------------------------------------------------------------------------------------
	 * Finalize script execution by processing errors and sending emails if required
	 * @param cTracking - tracking for error
	 * @returns nothing
	 */
	public void finalizeScriptExecution() {

		logger( "Finalizing overall script tracking detail")

		// Set tracking result based on script execution
		if ( iTracking_.tracking_.result != iTracking_.RESULT_START_ ) {  // tracking error reported?
			sendEmail(iTracking_);    // send email report for exception/reject errors
		} else {    // success
			iTracking_.updateResult(iTracking_.RESULT_SUCCESS_);

			// Debug mode will send all success runs to email
			if( this.bDebug_ )   // debug?
				sendEmail(iTracking_);
		}

		// Add interface result trace to tracking object
		iTracking_.addTrackingDetail(iTracking_.TRACKING_DETAIL_INT_LOG_, iTracking_.TRACKING_STATUS_END_,"${iTracking_.INTERFACE_} Log", "Interface results = ${iTracking_.tracking_.result}", sLoggerOutputBuf_.toString());
	}

	/** ------------------------------------------------------------------------------------
	 *  Send Email to distribution list.
	 */
	public void sendEmail( MyInterfaceTracking cTracking, boolean bIsCaseTracking= false ) {
		if (!this.cRule_.mailManager.isMailActive() || !cTracking ) {
			logger("eMail service is disabled or invalid tracking record");
			return;
		}

		StringBuilder body = new StringBuilder();
		String subject = "$iTracking_.INTERFACE_ BR " + ((this.bDebug_)?'Debug':'Error');
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
			if( !StringUtil.isNullOrEmpty(cTracking.tracking_.caseNumber) )
				body.append("Case#: ${cTracking.tracking_.caseNumber}<br>");
			body.append("<br>");

			body.append("Tracking Results:<br>");
			body.append("---------------------------------------------------------<br>");
			body.append("Tracking Id: ${cTracking.getID()}<br>");
			body.append("Result: ${cTracking.tracking_.result}<br>");
			if( !StringUtil.isNullOrEmpty(cTracking.tracking_.exception) )    // exception on main interface?
				body.append("Exception: ${cTracking.tracking_.exception}<br>");
			body.append("<br>");

			body.append("Interface BR Script Log:<br>");
			body.append("---------------------------------------------------------<br>");
			if( bIsCaseTracking ) // tracking individual case?
				body.append(sCaseLoggerOutputBuf_.toString());
			else
				body.append(sLoggerOutputBuf_.toString());

			// Dispatch emails to email support group
			if( !StringUtil.isNullOrEmpty(cSysProps_.sEmailList_) ) { // valid emails?
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
		sLoggerOutputBuf_.append(sBuf + "<br>")
		sCaseLoggerOutputBuf_.append(sBuf + "<br>")
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
	ValidationError( boolean bReject, String cCaseNum, String sDesc, int nType=0) {
		this.bReject_= bReject;
		this.sCaseNum_= cCaseNum;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date()    // get current date and time
	}

	// Constructor
	ValidationError( boolean bReject, String sDesc, int nType=0) {
		this.bReject_= bReject;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date()    // get current date and time
	}

	// Constructor
	ValidationError( boolean bReject, String cCaseNum, String sFilename, String sDesc, int nType=0) {
		this.bReject_= bReject;
		this.sCaseNum_= cCaseNum;
		this.sFilename_= sFilename;
		this.sDesc_ = sDesc;
		this.nType_ = nType;
		this.dDate_ = new Date()    // get current date and time
	}

	// Error header
	String outputTextErrorHeader() {
		DateFormat df = new SimpleDateFormat("yyyy.MM.dd-HH:mm");
		String sOutput = "Error Report - (Date:${df.format(dDate_)}, Case#:$sCaseNum_)";
		return sOutput;
	}

	// Error row
	String outputTextErrorRow() {
		String output = sDesc_;
		return output;
	}
  String summary(){
    return "cCaseNum: ${this.sCaseNum_}; bProcessed_: ${this.bProcessed_}; bReject_: ${this.bReject_}; sDesc_: ${this.sDesc_}; sFilename_: ${this.sFilename_}; nType_: ${this.nType_}; dDate_: ${this.dDate_}".toString();
  }
}

/** ------------------------------------------------------------------------------------
 * Charge statute object for charge statute tracking
 */
class ChargeStatuteObj {
    String sChargeNumber_;
    String sStatuteMemo_;

    // Constructor
    ChargeStatuteObj( String sChargeNbr, String sStatuteMemo ){
        this.sChargeNumber_ = sChargeNbr;
        this.sStatuteMemo_ = sStatuteMemo;
    }
}



/** --------------------------------------------------------------------------------------
 * Case party object for tracking xml/ePros parties
 */
class CasePartyObj {
	public Object oCasePartyObj_ = null;	// xml caseParty section
	public Party cParty_ = null;	// eSuite party

	// Constructors
	CasePartyObj(){};
	CasePartyObj(Party cParty, Object oCasePartyObj) {
		oCasePartyObj_ = oCasePartyObj;
		cParty_ = cParty;
	}
	public boolean isValid(){ return( (cParty_ && oCasePartyObj_) ); }

	// Get xml case party attribute ID
	public String getCasePartyId(){
		return ( (oCasePartyObj_ != null)? (String)oCasePartyObj_.@'ID': null );
	}
}

/** --------------------------------------------------------------------------------------
 * Person name object. Format expect a "lastname, firstname middlename, suffix" format.
 * The lastname attribute will contain the lastname and firstname attribute will contain
 * everything after first comma.
 */
class PersonNameObj {
	String firstName = "";
	String lastName = "";

	PersonNameObj(){}

	PersonNameObj( String sPersonName ) {
		parsePersonName(sPersonName);
	}

	// Parse name object and load attributes
	public void parsePersonName( String sPersonName ) {
		try {
			if (!StringUtil.isNullOrEmpty(sPersonName)) {
				int index = sPersonName.indexOf(',');
				println "Index =" + index;
				if (index > 0) {  // csv?
					lastName = sPersonName.substring(0, index);
					firstName = sPersonName.substring(index + 1).replaceAll("^ *", "")
				}
			}
		}catch (Exception ex) {
			throw new Exception("Exception::parsePersonName - error parsing name " + ex.message);
		}
	}

	// Test for valid first/last name
	public boolean isValid() {
		return ((firstName?.trim()?.length() > 0 || lastName?.trim()?.length() > 0 ));
	}
}






