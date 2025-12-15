package chat_gpt.tests;

public enum Expressions
{
	E1(new String[]
	{
		"- ReceiveInvoices < Secretariat < ForwardInvoices\n",
		"- (IdentifySuppliers, CreateInvoiceInstance) < (CheckInvoiceItems, NoteCostCenters)\n",
		"- (CheckInvoiceItems, NoteCostCenters, ForwardInvoices) < SendToCostCenterManager\n",
		"- SendToCostCenterManager < (ReviewInvoiceAccuracy, MarkPositionAccurate | ResolveInconsistencies)" +
				"< ConsultCostCenterManagers\n",
		"- (ReviewInvoiceAccuracy, MarkPositionAccurate, ResolveInconsistencies, ConsultCostCenterManagers)" +
				"< ForwardToCommercialManager\n",
		"- ForwardToCommercialManager < (CommercialAudit, IssuePaymentApproval)\n",
		"- (CommercialAudit, IssuePaymentApproval) < CheckFourEyesPrinciple\n",
		"- (ReviewInvoiceAccuracy, MarkPositionAccurate, ResolveInconsistencies, ConsultCostCenterManagers," +
				"ForwardToCommercialManager, CheckFourEyesPrinciple) < ResolveComplaints\n",
		"- (ReviewInvoiceAccuracy, MarkPositionAccurate, ResolveInconsistencies, ConsultCostCenterManagers," +
				"ForwardToCommercialManager, CheckFourEyesPrinciple, ResolveComplaints) < (GivePaymentInstructions," +
				"CloseInvoiceInstance)\n"
	}),

	E2_V1(new String[]
	{
		"- ProcessApplication\n",
		"- ProcessApplication < ((RetrieveCustomerProfile, AnalyseCustomerProfile) | CreateProfile)\n",
		"- (RetrieveCustomerProfile, AnalyseCustomerProfile, CreateProfile) < (IdentifyAccountType," +
				"PrepareAccountOpening)\n",
		"- (IdentifyAccountType, PrepareAccountOpening) < (ReceiveSupportDocuments, UpdateInfoRecords) &" +
				"(BackgroundVerification & ReviewApplication)\n",
		"- (ReceiveSupportDocuments, UpdateInfoRecords, BackgroundVerification, ReviewApplication) < (NotifyRejection" +
				"| (GenerateAccountNumber, SendStarterKit, ActivateAccount))\n"
	}),

	E2_V2(new String[]
	{
		"- ProcessApplication\n",
		"- ProcessApplication < (RetrieveCustomerProfile, AnalyseCustomerProfile)\n",
		"- (RetrieveCustomerProfile, AnalyseCustomerProfile) | CreateProfile\n",
		"- (RetrieveCustomerProfile, AnalyseCustomerProfile, CreateProfile) < (IdentifyAccountType, PrepareAccountOpening)\n",
		"- (IdentifyAccountType, PrepareAccountOpening) < (ReceiveSupportDocuments, UpdateInfoRecords," +
				"BackgroundVerification, ReviewApplication)\n",
		"- (ReceiveSupportDocuments, UpdateInfoRecords, BackgroundVerification, ReviewApplication) < (NotifyRejection" +
				"| (GenerateAccountNumber, SendStarterKit, ActivateAccount))\n"
	}),

	BLUEPRINT(new String[]
	{
		"- LogsInPlatform\n",
		"- LogsInPlatform < LogsInMetamask < AccesToSubmissionPortal\n",
		"- (LogsInPlatform, LogsInMetamask, AccesToSubmissionPortal) < UploadsFile\n",
		"- UploadsFile < VerifiesFiles < (SendBackToSubmissionPortal < AccesToSubmissionPortal, SubmitsForAttestation)\n",
		"- SubmitsForAttestation < RecordAttestation\n",
		"- RecordAttestation < (SendsAttestedBlueprint < ReceiveTheBlueprint)\n",
		"- (SendsAttestedBlueprint, RecordAttestation) < (SendsErrorMessage, RecordAttestation)\n"
	}),

	BUSINESS_TRIP_ORGANIZATION(new String[]
	{
		"- MissionAuthorization\n",
		"- MissionAuthorization < BookFlight\n",
		"- BookFlight < MissionPaperwork\n",
		"- BookFlight < (BookHotel, TakeInsurance, CheckLocalTransportation, DoVaccination)\n",
		"- (BookHotel, MissionPaperwork) < VisaProcess\n",
		"- (MissionAuthorization, BookFlight, MissionPaperwork, BookHotel, TakeInsurance, CheckLocalTransportation," +
				"DoVaccination, VisaProcess) < ArchiveMission\n"
	}),

	EMPLOYEE_HIRING(new String[]
	{
		"- FillInForms < MedicalCheckUp < (VisaApplication | VisaVerification)\n",
		"- (FillInForms, MedicalCheckUp, VisaApplication, VisaVerification) < (RejectVisa < (FillInForms) |" +
				"(ValidatePartially, AskForAdditionalDocuments) | Validate)\n",
		"- (FillInForms, MedicalCheckUp, VisaApplication, VisaVerification, RejectVisa, ValidatePartially," +
				"AskForAdditionalDocuments, Validate) < (UpdatePersonnelDatabase < (AnticipateWages &" +
				"PrepareWelcomeKit) < ArchiveAllDocuments)\n"
	}),

	TEST(new String[]
	{
		"- ReceiveInvoices < ForwardToAccountingEmployee\n",
		"- ForwardToAccountingEmployee < (IdentifyChargingSuppliers, CreateNewInstance)\n",
		"- (IdentifyChargingSuppliers, CreateNewInstance) < (CheckInvoiceItems, NoteCorrespondingAndRelatedCostCenter)*\n",
		"- (CheckInvoiceItems, NoteCorrespondingAndRelatedCostCenter) < SendToFirstCostCenterManager\n",
		"- SendToFirstCostCenterManager < ReviewContentForAccuracy\n",
		"- (ReviewContentForAccuracy, NoteCodeOne) < ReturnInvoiceCopy\n",
		"- ReturnInvoiceCopy < (SendToNextCostCenterManager | SendBackToAccounting)\n",
		"- ReviewContentForAccuracy < RejectAP < ForwardToAccountingEmployee\n",
		"- (RejectAP, NoteCodeOne, ReturnInvoiceCopy, SendToNextCostCenterManager, SendBackToAccounting) <" +
				"(IdentifyChargingSuppliers, CreateNewInstance, CheckInvoiceItems, NoteCorrespondingAndRelatedCostCenter," +
				"SendToFirstCostCenterManager, ReviewContentForAccuracy, RejectAP, ForwardToAccountingEmployee," +
				"ClarifyWithVendor, ConsultCostCenterManager, ForwardInvoiceCopyToCommercialManager, CommercialAudit," +
				"IssuePaymentApproval, CheckPaymentAgain, ResolveComplaint, GivePaymentInstruction, CloseInstance)\n",
		"- (IdentifyChargingSuppliers, CreateNewInstance, CheckInvoiceItems, NoteCorrespondingAndRelatedCostCenter," +
				"SendToFirstCostCenterManager, ReviewContentForAccuracy, RejectAP, NoteCodeOne, ReturnInvoiceCopy," +
				"SendToNextCostCenterManager, SendBackToAccounting) < (ClarifyWithVendor, ConsultCostCenterManager)\n",
		"- (ClarifyWithVendor, ConsultCostCenterManager) < ForwardInvoiceCopyToCommercialManager\n",
		"- ForwardInvoiceCopyToCommercialManager < (CommercialAudit, IssuePaymentApproval)\n",
		"- CommercialAudit < CheckPaymentAgain\n",
		"- CheckPaymentAgain < CheckPaymentAgain\n",
		"- CommercialAudit < ResolveComplaint\n",
		"- ResolveComplaint < GivePaymentInstruction\n",
		"- GivePaymentInstruction < CloseInstance\n"
	}),

	BICYCLE_MANUFACTURING(new String[]
	{
		"- ReceiveOrder < SendOrderToSaleDepartment\n",
		"- SendOrderToSaleDepartment < (RejectOrder | AcceptOrder)\n",
		"- AcceptOrder < InformStorehouseAndEngineeringDepartment\n",
		"- (ProcessOrderPartList, CheckRequiredQuantities) < (ReservePart | BackOrder)*\n",
		"- (ReservePart, BackOrder)* < PrepareComponentsForAssembling\n",
		"- (PrepareComponentsForAssembling, (ReservePart, BackOrder)*) < AssemblesBicycle\n",
		"- AssemblesBicycle < ShipBicycleToCustomer\n"
	}),

	BIZAGI_2(new String[]
	{
		"- SubmitsOfficeSupplyRequest < ReceiveRequest\n",
		"- ReceiveRequest < (Approve | AskForChange | RejectRequest)\n",
		"- RejectRequest\n",
		"- (AskForChange < (ReviewComments, MakeChanges)) < SubmitsOfficeSupplyRequest\n",
		"- Approve < (MakeQuotations & SelectVendor)\n",
		"- SelectVendor\n",
		"- SelectVendor < (GenerateAndSendPurchaseOrder, WaitForProductDelivery, WaitForInvoice)\n",
		"- GenerateAndSendPurchaseOrder < WaitForProductDelivery < WaitForInvoice\n",
		"- (MakeQuotations, SelectVendor, GenerateAndSendPurchaseOrder, WaitForProductDelivery, WaitForInvoice) < SendNotification\n"
	}),

	SIMPLE(new String[]
	{
		"- (DDD, GDG) < A\n",
		"- A < B\n",
		"- B < C\n",
		"- C < (F < G)\n"
	}),

	ONLY_PAR_1(new String[]
	{
		"- A & B\n"
	}),

	ONLY_SEQ_1(new String[]
	{
		"- A < B\n"
	}),

	BASIC_SELF_LOOP(new String[]
	{
		"- A < A\n"
	}),

	ONLY_LOOP_1(new String[]
	{
		"- A < B < A\n"
	}),

	ONLY_LOOP_2(new String[]
	{
		"- (A,B)*\n"
	}),

	ONLY_LOOP_3(new String[]
	{
		"- (A | B)*\n"
	}),

	ONLY_LOOP_4(new String[]
	{
		"- (A < B)*\n"
	}),

	LOOP_WITH_CHOICE(new String[]
	{
		"- (A < B)*\n",
		"- B | C\n"
	}),

	SIMPLE_2(new String[]
	{
		"- A < B\n",
		"- B < D\n",
		"- C < B\n",
		"- E\n"
	}),

	SIMPLE_3(new String[]
	{
		"- A < B\n",
		"- B < D\n",
		"- C < B\n",
		"- E < (A, B, C, D, GGGG)\n",
		"- GGGG\n"
	}),

	CLAIM_NOTIFICATION(new String[]
	{
		"- CheckWhetherClaimantIsInsuredByOrganization < InformClaimantOfRejection\n",
		"- CheckWhetherClaimantIsInsuredByOrganization < EvaluateClaimSeverity < SendRelevantFormsToClaimant\n",
		"- SendRelevantFormsToClaimant < CheckFormsForCompleteness < (RegisterClaim | InformClaimToUpdateForms <" +
				"CheckFormsForCompleteness)\n"
	}),

	//TODO Essayer de trouver une solution pour ce bug très précis (~/these/Natural_language_to_BPMN/known_bugs)
	COMPUTER_REPAIR(new String[]
	{
		"- BringsDefectiveComputer < (CheckDefect, HandsOutRepairCost)\n",
		"- (CheckDefect, HandsOutRepairCost) < (CheckAndRepairHardware & CheckAndConfigureSoftware)\n",
		"- (CheckAndRepairHardware, CheckAndConfigureSoftware) < TestProperSystemFunctionality\n",
		"- TestProperSystemFunctionality < (CheckAndRepairHardware, CheckAndConfigureSoftware)\n",
		"- (CheckAndRepairHardware, CheckAndConfigureSoftware) < TestProperSystemFunctionality\n"
	}),

	EVENT_BASED_GATEWAY(new String[]
	{
		"- SendQuestionnaireToClaimant\n",
		"- SendQuestionnaireToClaimant < ReturnQuestionnaire\n",
		"- ReturnQuestionnaire < (ReceiveReminder < ReceiveReminder)\n",
		"- ReturnCompletedQuestionnaire < ManageCompletedQuestionnaire\n"
	}),

	EXERCISE_2(new String[]
	{
		"- (SendMortgageOffer, WaitForReply)\n",
		"- (SendMortgageOffer, WaitForReply) < (CallOrWriteToDeclineMortgage, UpdateCaseDetails, ArchiveWorkForCancellation)\n",
		"- (SendMortgageOffer, WaitForReply) < (SendBackCompletedOfferDocuments, AttachAllPrerequisiteDocuments) < CaseMoveToAdministration\n",
		"- (SendBackCompletedOfferDocuments, AttachAllPrerequisiteDocuments) < CaseMoveToAdministration\n",
		"- (SendBackCompletedOfferDocuments, AttachAllPrerequisiteDocuments) < (GenerateMessageRequestingOutstandingDocuments)\n",
		"- (SendMortgageOffer, WaitForReply) < (CallOrWriteToDeclineMortgage, UpdateCaseDetails, ArchiveWorkForCancellation)\n",
		"- (SendMortgageOffer, WaitForReply) < (UpdateCaseDetails, ArchiveWorkForCancellation)\n",
		"- (SendMortgageOffer, WaitForReply) < (UpdateCaseDetails, ArchiveWorkForCancellation) < (GenerateMessageRequestingOutstandingDocuments)\n",
		"- (GenerateMessageRequestingOutstandingDocuments) < (UpdateCaseDetails, ArchiveWorkForCancellation)\n"
	}),

	EXERCISE_5(new String[]
	{
		"- LocateAndDistributeRelevantExistingDesigns\n",
		"- LocateAndDistributeRelevantExistingDesigns < (DesignElectricalSystem & DesignPhysicalSystem) <" +
				"(ReviewDesign < UpdatePlan) < ReviseDesign < TestRevisedDesign\n",
		"- TestRevisedDesign < (UpdatePlan | (CombineDesigns, SendToManufacturing))\n"
	}),

	BPM_REVIEW_1(new String[]
	{
		"- A\n" +
		"- A < (B)*\n",
		"- (B, C) < (D < (E, F))\n",
		"- (A, B, C, D, E, F) < G\n"
	}),

	SYNC_TEST_1(new String[]
	{
		"- A < B\n",
		"- A < C\n",
		"- D < B\n",
		"- D < C\n"
	}),

	SYNX_TEST_2(new String[]
	{
		"- F < G\n",
		"- F < B\n",
		"- F < C\n",
		"- E < G\n",
		"- E < B\n",
		"- E < C\n",
		"- A < G\n",
		"- A < B\n",
		"- A < C\n",
	}),

	SYNC_TEST_3(new String[]
	{
		"- F < G\n",
		"- F < B\n",
		"- F < C\n",
		"- E < G\n",
		"- E < B\n",
		"- E < C\n",
		"- A < D\n",
		"- A < B\n",
		"- A < C\n",
	}),

	OTHER_TEST(new String[]
	{
		"- A < B\n",
		"- B < C\n",
		"- B < D\n",
		"- C < E\n",
		"- D < E\n",
		"- E < C\n",
		"- E < D\n"
	}),

	NORMALIZATION_TEST(new String[]
	{
		"- A < B < C < D < E < F < G < H < I < J < K < L < M < N < O < P < Q < R < S < T < U < V < W < X < Y < Z < ZEG < REG < erglkg < ERGeeM\n"
	}),

	TEST_2(new String[]{
		"- A < (B, C, D)\n"
	}),

	TEST_3(new String[]{
		"- A < (B, C, D) < A\n"
	});

	private final String value;

	Expressions(final String[] lines)
	{
		this.value = this.parseString(lines);
	}

	public String getValue()
	{
		return this.value;
	}

	public static String getExpressionToUse()
	{
		return Expressions.TEST_3.getValue();
	}

	//Private methods

	private String parseString(final String[] expressionsToUse)
	{
		final StringBuilder builder = new StringBuilder();

		for (String expression : expressionsToUse)
		{
			builder.append(expression.replace("\n", "\\n"));
		}

		return builder.toString();
	}
}
