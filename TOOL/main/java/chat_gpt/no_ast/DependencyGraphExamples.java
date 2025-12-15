package chat_gpt.no_ast;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessFactory;
import chat_gpt.ast_management.AbstractSyntaxNode;
import chat_gpt.ast_management.AbstractSyntaxNodeFactory;
import chat_gpt.ast_management.AbstractSyntaxTree;
import refactoring.legacy.dependencies.DependencyGraph;

import java.util.HashSet;

public class DependencyGraphExamples
{
	private DependencyGraphExamples()
	{

	}

	public static StructuredInformation EXAMPLE_1()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node Z = new Node(BpmnProcessFactory.generateTask("Z"));
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		final Node D = new Node(BpmnProcessFactory.generateTask("D"));
		final Node R = new Node(BpmnProcessFactory.generateTask("R"));
		final Node S = new Node(BpmnProcessFactory.generateTask("S"));
		final Node T = new Node(BpmnProcessFactory.generateTask("T"));
		final Node U = new Node(BpmnProcessFactory.generateTask("U"));
		final Node G = new Node(BpmnProcessFactory.generateTask("G"));
		final Node V = new Node(BpmnProcessFactory.generateTask("V"));
		final Node W = new Node(BpmnProcessFactory.generateTask("W"));
		final Node Y = new Node(BpmnProcessFactory.generateTask("Y"));
		final Node F = new Node(BpmnProcessFactory.generateTask("F"));
		final Node X = new Node(BpmnProcessFactory.generateTask("X"));
		final Node K = new Node(BpmnProcessFactory.generateTask("K"));

		//Abstract nodes
		final AbstractSyntaxNode absZ = AbstractSyntaxNodeFactory.newTask(Z.bpmnObject().id());
		final AbstractSyntaxNode absA = AbstractSyntaxNodeFactory.newTask(A.bpmnObject().id());
		final AbstractSyntaxNode absB = AbstractSyntaxNodeFactory.newTask(B.bpmnObject().id());
		final AbstractSyntaxNode absC = AbstractSyntaxNodeFactory.newTask(C.bpmnObject().id());
		final AbstractSyntaxNode absD = AbstractSyntaxNodeFactory.newTask(D.bpmnObject().id());
		final AbstractSyntaxNode absR = AbstractSyntaxNodeFactory.newTask(R.bpmnObject().id());
		final AbstractSyntaxNode absS = AbstractSyntaxNodeFactory.newTask(S.bpmnObject().id());
		final AbstractSyntaxNode absT = AbstractSyntaxNodeFactory.newTask(T.bpmnObject().id());
		final AbstractSyntaxNode absU = AbstractSyntaxNodeFactory.newTask(U.bpmnObject().id());
		final AbstractSyntaxNode absG = AbstractSyntaxNodeFactory.newTask(G.bpmnObject().id());
		final AbstractSyntaxNode absV = AbstractSyntaxNodeFactory.newTask(V.bpmnObject().id());
		final AbstractSyntaxNode absW = AbstractSyntaxNodeFactory.newTask(W.bpmnObject().id());
		final AbstractSyntaxNode absY = AbstractSyntaxNodeFactory.newTask(Y.bpmnObject().id());
		final AbstractSyntaxNode absF = AbstractSyntaxNodeFactory.newTask(F.bpmnObject().id());
		final AbstractSyntaxNode absX = AbstractSyntaxNodeFactory.newTask(X.bpmnObject().id());

		dependencyGraph.addInitialNode(Z);
		dependencyGraph.addInitialNode(F);
		dependencyGraph.addInitialNode(Y);

		//Y
		Y.addChildAndForceParent(V);
		Y.addChildAndForceParent(W);
		//Y.addChildAndForceParent(B);

		//Z
		Z.addChildAndForceParent(A);
		Z.addChildAndForceParent(Y);

		//A
		A.addChildAndForceParent(B);
		A.addChildAndForceParent(D);

		//B
		B.addChildAndForceParent(C);
		B.addChildAndForceParent(D);

		//C
		C.addChildAndForceParent(B);

		//D
		D.addChildAndForceParent(R);
		D.addChildAndForceParent(S);
		D.addChildAndForceParent(A);
		D.addChildAndForceParent(W);
		//D.addChildAndForceParent(Y);

		//R
		R.addChildAndForceParent(T);

		//S
		S.addChildAndForceParent(T);
		S.addChildAndForceParent(U);

		//T
		T.addChildAndForceParent(G);

		//U
		U.addChildAndForceParent(G);

		//G
		G.addChildAndForceParent(V);
		G.addChildAndForceParent(C);

		//F
		F.addChildAndForceParent(D);

		//V
		V.addChildAndForceParent(X);

		//Specified choices
		// A | Y
		final AbstractSyntaxTree choiceTree1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree1);
		choiceTree1.root().addSuccessor(absA);
		choiceTree1.root().addSuccessor(absY);

		// R | Y
		final AbstractSyntaxTree choiceTree2 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree2);
		choiceTree2.root().addSuccessor(absR);
		choiceTree2.root().addSuccessor(absY);

		// S | Y
		final AbstractSyntaxTree choiceTree3 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree3);
		choiceTree3.root().addSuccessor(absR);
		choiceTree3.root().addSuccessor(absY);

		// D | Y
		final AbstractSyntaxTree choiceTree4 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree4);
		choiceTree4.root().addSuccessor(absD);
		choiceTree4.root().addSuccessor(absY);

		// F | Y
		final AbstractSyntaxTree choiceTree5 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree5);
		choiceTree5.root().addSuccessor(absF);
		choiceTree5.root().addSuccessor(absY);

		// R | T (impossible)
		final AbstractSyntaxTree choiceTree6 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree6);
		choiceTree6.root().addSuccessor(absR);
		choiceTree6.root().addSuccessor(absT);

		// G | W
		/*final AbstractSyntaxTree choiceTree7 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree7);
		choiceTree7.root().addSuccessor(absS);
		choiceTree7.root().addSuccessor(absW);*/

		//Specified parallel
		// S & Y
		final AbstractSyntaxTree parallelTree1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newParallel());
		exampleElements.addExplicitParallel(parallelTree1);
		parallelTree1.root().addSuccessor(absR);
		parallelTree1.root().addSuccessor(absY);

		//Real loops
		// (D, R, T)*
		/*final HashSet<Node> realLoop1 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop1);
		realLoop1.add(D);
		realLoop1.add(R);
		realLoop1.add(T);*/

		// (B, C)*
		/*final HashSet<Node> realLoop2 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop2);
		realLoop2.add(B);
		realLoop2.add(C);*/

		// (A, B, D)*
		/*final HashSet<Node> realLoop3 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop3);
		realLoop3.add(A);
		realLoop3.add(B);
		realLoop3.add(D);*/

		// (S, B, D, T, G, C)*
		/*final HashSet<Node> realLoop4 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop4);
		realLoop4.add(S);
		realLoop4.add(B);
		realLoop4.add(D);
		realLoop4.add(T);
		realLoop4.add(G);
		realLoop4.add(C);*/

		// (S, B, D, T, G)*
		/*final HashSet<Node> realLoop5 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop5);
		realLoop5.add(S);
		realLoop5.add(B);
		realLoop5.add(D);
		realLoop5.add(T);
		realLoop5.add(G);*/

		// (S, R, D)*
		/*final HashSet<Node> realLoop6 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop6);
		realLoop6.add(S);
		realLoop6.add(R);
		realLoop6.add(D);*/

		// (T, U, G)*
		/*final HashSet<Node> realLoop7 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop7);
		realLoop7.add(T);
		realLoop7.add(U);
		realLoop7.add(G);*/

		// (D, C, V)*
		/*final HashSet<Node> realLoop8 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop8);
		realLoop8.add(D);
		realLoop8.add(C);
		realLoop8.add(V);*/

		// (S, T, U, G)*
		/*final HashSet<Node> realLoop9 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop9);
		realLoop9.add(S);
		realLoop9.add(T);
		realLoop9.add(U);
		realLoop9.add(G);*/

		// (S, T, D, G, R)*
		/*final HashSet<Node> realLoop10 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop10);
		realLoop10.add(S);
		realLoop10.add(T);
		realLoop10.add(D);
		realLoop10.add(G);
		realLoop10.add(R);*/

		// (D, W, S)*
		/*final HashSet<Node> realLoop11 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop11);
		realLoop11.add(D);
		realLoop11.add(W);
		realLoop11.add(S);*/

		// (D, S, G)*
		/*final HashSet<Node> realLoop12 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop12);
		realLoop12.add(D);
		realLoop12.add(S);
		realLoop12.add(G);*/

		// (Y, B, C, D, T, G)*
		/*final HashSet<Node> realLoop13 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop13);
		realLoop13.add(Y);
		realLoop13.add(B);
		realLoop13.add(C);
		realLoop13.add(D);
		realLoop13.add(T);
		realLoop13.add(G);*/

		// (S, T, U, K)*
		final HashSet<Node> realLoop14 = new HashSet<>();
		exampleElements.addExplicitLoop(realLoop14);
		realLoop14.add(S);
		realLoop14.add(T);
		realLoop14.add(U);
		realLoop14.add(K);

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_1_1()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node Z = new Node(BpmnProcessFactory.generateTask("Z"));
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		final Node D = new Node(BpmnProcessFactory.generateTask("D"));
		final Node R = new Node(BpmnProcessFactory.generateTask("R"));
		final Node S = new Node(BpmnProcessFactory.generateTask("S"));
		final Node T = new Node(BpmnProcessFactory.generateTask("T"));
		final Node U = new Node(BpmnProcessFactory.generateTask("U"));
		final Node G = new Node(BpmnProcessFactory.generateTask("G"));
		final Node V = new Node(BpmnProcessFactory.generateTask("V"));
		final Node X = new Node(BpmnProcessFactory.generateTask("X"));

		dependencyGraph.addInitialNode(Z);

		//Z
		Z.addChildAndForceParent(A);

		//A
		A.addChildAndForceParent(B);

		//B
		B.addChildAndForceParent(C);
		B.addChildAndForceParent(D);

		//C
		C.addChildAndForceParent(B);

		//D
		D.addChildAndForceParent(R);
		D.addChildAndForceParent(S);
		D.addChildAndForceParent(A);

		//R
		R.addChildAndForceParent(T);

		//S
		S.addChildAndForceParent(T);
		S.addChildAndForceParent(U);

		//T
		T.addChildAndForceParent(G);

		//U
		U.addChildAndForceParent(G);

		//G
		G.addChildAndForceParent(V);
		G.addChildAndForceParent(C);

		//V
		V.addChildAndForceParent(X);

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_2()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		dependencyGraph.addInitialNode(A);
		dependencyGraph.addInitialNode(B);
		dependencyGraph.addInitialNode(C);

		//Abstract nodes
		final AbstractSyntaxNode absA = AbstractSyntaxNodeFactory.newTask(A.bpmnObject().id());
		final AbstractSyntaxNode absB = AbstractSyntaxNodeFactory.newTask(B.bpmnObject().id());
		final AbstractSyntaxNode absC = AbstractSyntaxNodeFactory.newTask(C.bpmnObject().id());

		//Specified choices
		// A | B
		final AbstractSyntaxTree choiceTree1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree1);
		choiceTree1.root().addSuccessor(absA);
		choiceTree1.root().addSuccessor(absB);

		// A | C
		final AbstractSyntaxTree choiceTree2 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree2);
		choiceTree2.root().addSuccessor(absA);
		choiceTree2.root().addSuccessor(absC);

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_2_NO_INITIALS()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));

		//Abstract nodes
		final AbstractSyntaxNode absA = AbstractSyntaxNodeFactory.newTask(A.bpmnObject().id());
		final AbstractSyntaxNode absB = AbstractSyntaxNodeFactory.newTask(B.bpmnObject().id());
		final AbstractSyntaxNode absC = AbstractSyntaxNodeFactory.newTask(C.bpmnObject().id());

		//Specified choices
		// A | B
		final AbstractSyntaxTree choiceTree1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree1);
		choiceTree1.root().addSuccessor(absA);
		choiceTree1.root().addSuccessor(absB);

		// A | C
		final AbstractSyntaxTree choiceTree2 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree2);
		choiceTree2.root().addSuccessor(absA);
		choiceTree2.root().addSuccessor(absC);

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_3()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		final Node D = new Node(BpmnProcessFactory.generateTask("D"));

		//Abstract nodes
		final AbstractSyntaxNode absA = AbstractSyntaxNodeFactory.newTask(A.bpmnObject().id());
		final AbstractSyntaxNode absB = AbstractSyntaxNodeFactory.newTask(B.bpmnObject().id());
		final AbstractSyntaxNode absC = AbstractSyntaxNodeFactory.newTask(C.bpmnObject().id());

		//Initial nodes
		dependencyGraph.addInitialNode(A);

		//Sequences
		A.addChildAndForceParent(B);
		A.addChildAndForceParent(C);
		A.addChildAndForceParent(D);

		//Specified choices
		// B | C
		final AbstractSyntaxTree choiceTree1 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree1);
		choiceTree1.root().addSuccessor(absB);
		choiceTree1.root().addSuccessor(absC);

		/*// A | C
		final AbstractSyntaxTree choiceTree2 = new AbstractSyntaxTree(AbstractSyntaxNodeFactory.newChoice());
		exampleElements.addExplicitChoice(choiceTree2);
		choiceTree2.root().addSuccessor(absA);
		choiceTree2.root().addSuccessor(absC);*/

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_4()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		final Node D = new Node(BpmnProcessFactory.generateTask("D"));

		//Initial nodes
		dependencyGraph.addInitialNode(A);
		dependencyGraph.addInitialNode(D);

		//Sequences
		A.addChildAndForceParent(B);
		A.addChildAndForceParent(C);
		D.addChildAndForceParent(B);
		D.addChildAndForceParent(C);

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_5()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		final Node D = new Node(BpmnProcessFactory.generateTask("D"));

		//Initial nodes
		dependencyGraph.addInitialNode(A);
		dependencyGraph.addInitialNode(C);

		//Sequences
		A.addChildAndForceParent(B);
		C.addChildAndForceParent(B);
		C.addChildAndForceParent(D);

		return exampleElements;
	}

	public static StructuredInformation EXAMPLE_6()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node A = new Node(BpmnProcessFactory.generateTask("A"));
		final Node B = new Node(BpmnProcessFactory.generateTask("B"));
		final Node C = new Node(BpmnProcessFactory.generateTask("C"));
		final Node D = new Node(BpmnProcessFactory.generateTask("D"));

		//Initial nodes
		dependencyGraph.addInitialNode(A);
		dependencyGraph.addInitialNode(B);

		//End nodes
		dependencyGraph.addEndNode(C);
		dependencyGraph.addEndNode(D);

		//Sequences
		A.addChildAndForceParent(C);
		B.addChildAndForceParent(C);
		B.addChildAndForceParent(D);

		return exampleElements;
	}

	public static StructuredInformation RUNNING_EXAMPLE()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node StFMS = new Node(BpmnProcessFactory.generateTask("StartFeatureManagementSoftware", "Start Feature Management Software"));
		final Node DNFR = new Node(BpmnProcessFactory.generateTask("DescribeNewFeatureRequirements", "Describe New Feature Requirements"));
		final Node VI = new Node(BpmnProcessFactory.generateTask("VerifyInternally", "Verify Internally"));
		final Node VE = new Node(BpmnProcessFactory.generateTask("VerifyExternally", "Verify Externally"));
		final Node CNFB = new Node(BpmnProcessFactory.generateTask("CreateNewFeatureBranch", "Create New Feature Branch"));
		final Node STD = new Node(BpmnProcessFactory.generateTask("StartTechnicalDesign", "Start Technical Design"));
		final Node FD = new Node(BpmnProcessFactory.generateTask("FeatureDevelopment", "Feature Development"));
		final Node DP = new Node(BpmnProcessFactory.generateTask("DebuggingPhase", "Debugging Phase"));
		final Node BCO = new Node(BpmnProcessFactory.generateTask("BugCaseOpening", "Bug Case Opening"));
		final Node FSDP = new Node(BpmnProcessFactory.generateTask("FirstStageDebugPhase", "First Stage Debug Phase"));
		final Node CFLR = new Node(BpmnProcessFactory.generateTask("ClosingFirstLevelRequest", "Closing First Level Request"));
		final Node SSDP = new Node(BpmnProcessFactory.generateTask("SecondStageDebugPhase", "Second Stage Debug Phase"));
		final Node CSLR = new Node(BpmnProcessFactory.generateTask("ClosingSecondLevelRequest", "Closing Second Level Request"));
		final Node TSDP = new Node(BpmnProcessFactory.generateTask("ThirdStageDebugPhase", "Third Stage Debug Phase"));
		final Node CTLR = new Node(BpmnProcessFactory.generateTask("ClosingThirdLevelRequest", "Closing Third Level Request"));
		final Node RF = new Node(BpmnProcessFactory.generateTask("ReleaseFeature", "Release Feature"));
		final Node ShFMS = new Node(BpmnProcessFactory.generateTask("ShutdownFeatureManagementSoftware", "Shutdown Feature Management Software"));

		//TODO Temporary while loops are not properly handled
		CFLR.addChildAndForceParent(SSDP);
		CSLR.addChildAndForceParent(TSDP);

		//Initial nodes
		dependencyGraph.addInitialNode(StFMS);

		//End nodes
		dependencyGraph.addEndNode(ShFMS);

		//Sequences
		//StFMS
		StFMS.addChildAndForceParent(DNFR);

		//DNFR
		DNFR.addChildAndForceParent(VI);
		DNFR.addChildAndForceParent(VE);

		//VI
		VI.addChildAndForceParent(CNFB);
		VI.addChildAndForceParent(STD);

		//VE
		VE.addChildAndForceParent(STD);

		//CNFB
		CNFB.addChildAndForceParent(FD);

		//STD
		STD.addChildAndForceParent(FD);

		//FD
		FD.addChildAndForceParent(DP);

		//DP
		DP.addChildAndForceParent(BCO);
		DP.addChildAndForceParent(RF); //TODO A gérer mieux : se fait supprimer par la réduction transitive

		//BCO
		BCO.addChildAndForceParent(FSDP);
		BCO.addChildAndForceParent(SSDP);
		BCO.addChildAndForceParent(TSDP);

		//FSDP
		FSDP.addChildAndForceParent(CFLR);

		//SSDP
		SSDP.addChildAndForceParent(CSLR);

		//TSDP
		TSDP.addChildAndForceParent(CTLR);

		//CFLR
		CFLR.addChildAndForceParent(DP);
		CFLR.addChildAndForceParent(RF);

		//CSLR
		CSLR.addChildAndForceParent(DP);
		CSLR.addChildAndForceParent(RF);

		//CTLR
		CTLR.addChildAndForceParent(DP);
		CTLR.addChildAndForceParent(RF);

		//RF
		RF.addChildAndForceParent(ShFMS);
		RF.addChildAndForceParent(DNFR); //TODO Handle the troublemaker

		return exampleElements;
	}

	public static StructuredInformation FINAL_RUNNING_EXAMPLE()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node StFMS = new Node(BpmnProcessFactory.generateTask("StartFeatureManagementSoftware", "Start Feature Management Software"));
		final Node DNFR = new Node(BpmnProcessFactory.generateTask("DescribeNewFeatureRequirements", "Describe New Feature Requirements"));
		final Node VI = new Node(BpmnProcessFactory.generateTask("VerifyInternally", "Verify Internally"));
		final Node VE = new Node(BpmnProcessFactory.generateTask("VerifyExternally", "Verify Externally"));
		final Node CNFB = new Node(BpmnProcessFactory.generateTask("CreateNewFeatureBranch", "Create New Feature Branch"));
		final Node STD = new Node(BpmnProcessFactory.generateTask("StartTechnicalDesign", "Start Technical Design"));
		final Node FD = new Node(BpmnProcessFactory.generateTask("FeatureDevelopment", "Feature Development"));
		final Node DP = new Node(BpmnProcessFactory.generateTask("DebuggingPhase", "Debugging Phase"));
		final Node BCO = new Node(BpmnProcessFactory.generateTask("BugCaseOpening", "Bug Case Opening"));
		final Node FSDP = new Node(BpmnProcessFactory.generateTask("FirstStageDebugPhase", "First Stage Debug Phase"));
		final Node CFLR = new Node(BpmnProcessFactory.generateTask("ClosingFirstLevelRequest", "Closing First Level Request"));
		final Node SSDP = new Node(BpmnProcessFactory.generateTask("SecondStageDebugPhase", "Second Stage Debug Phase"));
		final Node CSLR = new Node(BpmnProcessFactory.generateTask("ClosingSecondLevelRequest", "Closing Second Level Request"));
		final Node TSDP = new Node(BpmnProcessFactory.generateTask("ThirdStageDebugPhase", "Third Stage Debug Phase"));
		final Node CTLR = new Node(BpmnProcessFactory.generateTask("ClosingThirdLevelRequest", "Closing Third Level Request"));
		final Node RF = new Node(BpmnProcessFactory.generateTask("ReleaseFeature", "Release Feature"));
		final Node ShFMS = new Node(BpmnProcessFactory.generateTask("ShutdownFeatureManagementSoftware", "Shutdown Feature Management Software"));
		final Node EXCLUSIVE_SPLIT_1 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES1"));
		final Node EXCLUSIVE_SPLIT_2 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES2"));
		final Node EXCLUSIVE_SPLIT_3 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES3"));
		final Node EXCLUSIVE_SPLIT_4 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES4"));
		final Node EXCLUSIVE_SPLIT_5 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES5"));
		final Node EXCLUSIVE_SPLIT_6 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES6"));
		final Node PARALLEL_SPLIT_1 = new Node(BpmnProcessFactory.generateParallelSplitGateway("PS1")); //TODO
		final Node PARALLEL_SPLIT_2 = new Node(BpmnProcessFactory.generateParallelSplitGateway("PS2"));
		final Node EXCLUSIVE_MERGE_1 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM1"));
		final Node EXCLUSIVE_MERGE_2 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM2"));
		final Node EXCLUSIVE_MERGE_3 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM3"));
		final Node EXCLUSIVE_MERGE_4 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM4"));
		final Node EXCLUSIVE_MERGE_5 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM5"));
		final Node PARALLEL_MERGE_1 = new Node(BpmnProcessFactory.generateParallelMergeGateway("PM1"));
		final Node PARALLEL_MERGE_2 = new Node(BpmnProcessFactory.generateParallelMergeGateway("PM2"));

		//Initial nodes
		dependencyGraph.addInitialNode(StFMS);

		//End nodes
		dependencyGraph.addEndNode(ShFMS);

		//Sequences
		//StFMS
		StFMS.addChildAndForceParent(EXCLUSIVE_MERGE_1);

		//Exclusive merge 1
		EXCLUSIVE_MERGE_1.addChildAndForceParent(DNFR);

		//DNFR
		DNFR.addChildAndForceParent(PARALLEL_SPLIT_1);

		//Parallel split 1
		PARALLEL_SPLIT_1.addChildAndForceParent(VI);
		PARALLEL_SPLIT_1.addChildAndForceParent(VE);

		//VI
		VI.addChildAndForceParent(PARALLEL_SPLIT_2);

		//Parallel split 2
		PARALLEL_SPLIT_2.addChildAndForceParent(CNFB);
		PARALLEL_SPLIT_2.addChildAndForceParent(PARALLEL_MERGE_1);

		//VE
		VE.addChildAndForceParent(PARALLEL_MERGE_1);

		//Parallel merge 1
		PARALLEL_MERGE_1.addChildAndForceParent(STD);

		//CNFB
		CNFB.addChildAndForceParent(PARALLEL_MERGE_2);

		//STD
		STD.addChildAndForceParent(PARALLEL_MERGE_2);

		//Parallel merge 2
		PARALLEL_MERGE_2.addChildAndForceParent(FD);

		//FD
		FD.addChildAndForceParent(EXCLUSIVE_MERGE_2);

		//Exclusive merge 2
		EXCLUSIVE_MERGE_2.addChildAndForceParent(DP);

		//DP
		DP.addChildAndForceParent(EXCLUSIVE_SPLIT_1);

		//Exclusive split 1
		EXCLUSIVE_SPLIT_1.addChildAndForceParent(BCO);
		EXCLUSIVE_SPLIT_1.addChildAndForceParent(EXCLUSIVE_MERGE_5); //TODO A gérer mieux : se fait supprimer par la réduction transitive

		//BCO
		BCO.addChildAndForceParent(EXCLUSIVE_SPLIT_2);

		//Exclusive split 2
		EXCLUSIVE_SPLIT_2.addChildAndForceParent(FSDP);
		EXCLUSIVE_SPLIT_2.addChildAndForceParent(EXCLUSIVE_MERGE_3);
		EXCLUSIVE_SPLIT_2.addChildAndForceParent(EXCLUSIVE_MERGE_4);

		//Exclusive merge 3
		EXCLUSIVE_MERGE_3.addChildAndForceParent(TSDP);

		//Exclusive merge 4
		EXCLUSIVE_MERGE_4.addChildAndForceParent(SSDP);

		//FSDP
		FSDP.addChildAndForceParent(CFLR);

		//SSDP
		SSDP.addChildAndForceParent(CSLR);

		//TSDP
		TSDP.addChildAndForceParent(CTLR);

		//CFLR
		CFLR.addChildAndForceParent(EXCLUSIVE_SPLIT_3);

		//Exclusive split 3
		EXCLUSIVE_SPLIT_3.addChildAndForceParent(EXCLUSIVE_MERGE_4);
		EXCLUSIVE_SPLIT_3.addChildAndForceParent(EXCLUSIVE_MERGE_2);
		EXCLUSIVE_SPLIT_3.addChildAndForceParent(EXCLUSIVE_MERGE_5);

		//CSLR
		CSLR.addChildAndForceParent(EXCLUSIVE_SPLIT_4);

		//Exclusive split 4
		EXCLUSIVE_SPLIT_4.addChildAndForceParent(EXCLUSIVE_MERGE_3);
		EXCLUSIVE_SPLIT_4.addChildAndForceParent(EXCLUSIVE_MERGE_2);
		EXCLUSIVE_SPLIT_4.addChildAndForceParent(EXCLUSIVE_MERGE_5);

		//CTLR
		CTLR.addChildAndForceParent(EXCLUSIVE_SPLIT_5);

		//Exclusive split 5
		EXCLUSIVE_SPLIT_5.addChildAndForceParent(EXCLUSIVE_MERGE_2);
		EXCLUSIVE_SPLIT_5.addChildAndForceParent(EXCLUSIVE_MERGE_5);

		//Exclusive merge 5
		EXCLUSIVE_MERGE_5.addChildAndForceParent(RF);

		//RF
		RF.addChildAndForceParent(EXCLUSIVE_SPLIT_6);

		//Exclusive split 6
		EXCLUSIVE_SPLIT_6.addChildAndForceParent(ShFMS);
		EXCLUSIVE_SPLIT_6.addChildAndForceParent(EXCLUSIVE_MERGE_1);

		return exampleElements;
	}

	public static StructuredInformation FULLY_EXCLUSIVE_RUNNING_EXAMPLE()
	{
		final DependencyGraph dependencyGraph = new DependencyGraph();
		final StructuredInformation exampleElements = new StructuredInformation(dependencyGraph);

		//Nodes
		final Node StFMS = new Node(BpmnProcessFactory.generateTask("StartFeatureManagementSoftware", "Start Feature Management Software"));
		final Node DNFR = new Node(BpmnProcessFactory.generateTask("DescribeNewFeatureRequirements", "Describe New Feature Requirements"));
		final Node VI = new Node(BpmnProcessFactory.generateTask("VerifyInternally", "Verify Internally"));
		final Node VE = new Node(BpmnProcessFactory.generateTask("VerifyExternally", "Verify Externally"));
		final Node CNFB = new Node(BpmnProcessFactory.generateTask("CreateNewFeatureBranch", "Create New Feature Branch"));
		final Node STD = new Node(BpmnProcessFactory.generateTask("StartTechnicalDesign", "Start Technical Design"));
		final Node FD = new Node(BpmnProcessFactory.generateTask("FeatureDevelopment", "Feature Development"));
		final Node DP = new Node(BpmnProcessFactory.generateTask("DebuggingPhase", "Debugging Phase"));
		final Node BCO = new Node(BpmnProcessFactory.generateTask("BugCaseOpening", "Bug Case Opening"));
		final Node FSDP = new Node(BpmnProcessFactory.generateTask("FirstStageDebugPhase", "First Stage Debug Phase"));
		final Node CFLR = new Node(BpmnProcessFactory.generateTask("ClosingFirstLevelRequest", "Closing First Level Request"));
		final Node SSDP = new Node(BpmnProcessFactory.generateTask("SecondStageDebugPhase", "Second Stage Debug Phase"));
		final Node CSLR = new Node(BpmnProcessFactory.generateTask("ClosingSecondLevelRequest", "Closing Second Level Request"));
		final Node TSDP = new Node(BpmnProcessFactory.generateTask("ThirdStageDebugPhase", "Third Stage Debug Phase"));
		final Node CTLR = new Node(BpmnProcessFactory.generateTask("ClosingThirdLevelRequest", "Closing Third Level Request"));
		final Node RF = new Node(BpmnProcessFactory.generateTask("ReleaseFeature", "Release Feature"));
		final Node ShFMS = new Node(BpmnProcessFactory.generateTask("ShutdownFeatureManagementSoftware", "Shutdown Feature Management Software"));
		final Node EXCLUSIVE_SPLIT_1 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES1"));
		final Node EXCLUSIVE_SPLIT_2 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES2"));
		final Node EXCLUSIVE_SPLIT_3 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES3"));
		final Node EXCLUSIVE_SPLIT_4 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES4"));
		final Node EXCLUSIVE_SPLIT_5 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES5"));
		final Node EXCLUSIVE_SPLIT_6 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("ES6"));
		final Node PARALLEL_SPLIT_1 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("PS1")); //TODO
		final Node PARALLEL_SPLIT_2 = new Node(BpmnProcessFactory.generateExclusiveSplitGateway("PS2"));
		final Node EXCLUSIVE_MERGE_1 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM1"));
		final Node EXCLUSIVE_MERGE_2 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM2"));
		final Node EXCLUSIVE_MERGE_3 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM3"));
		final Node EXCLUSIVE_MERGE_4 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM4"));
		final Node EXCLUSIVE_MERGE_5 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("EM5"));
		final Node PARALLEL_MERGE_1 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("PM1"));
		final Node PARALLEL_MERGE_2 = new Node(BpmnProcessFactory.generateExclusiveMergeGateway("PM2"));

		//Initial nodes
		dependencyGraph.addInitialNode(StFMS);

		//End nodes
		dependencyGraph.addEndNode(ShFMS);

		//Sequences
		//StFMS
		StFMS.addChildAndForceParent(EXCLUSIVE_MERGE_1);

		//Exclusive merge 1
		EXCLUSIVE_MERGE_1.addChildAndForceParent(DNFR);

		//DNFR
		DNFR.addChildAndForceParent(PARALLEL_SPLIT_1);

		//Parallel split 1
		PARALLEL_SPLIT_1.addChildAndForceParent(VI);
		PARALLEL_SPLIT_1.addChildAndForceParent(VE);

		//VI
		VI.addChildAndForceParent(PARALLEL_SPLIT_2);

		//Parallel split 2
		PARALLEL_SPLIT_2.addChildAndForceParent(CNFB);
		PARALLEL_SPLIT_2.addChildAndForceParent(PARALLEL_MERGE_1);

		//VE
		VE.addChildAndForceParent(PARALLEL_MERGE_1);

		//Parallel merge 1
		PARALLEL_MERGE_1.addChildAndForceParent(STD);

		//CNFB
		CNFB.addChildAndForceParent(PARALLEL_MERGE_2);

		//STD
		STD.addChildAndForceParent(PARALLEL_MERGE_2);

		//Parallel merge 2
		PARALLEL_MERGE_2.addChildAndForceParent(FD);

		//FD
		FD.addChildAndForceParent(EXCLUSIVE_MERGE_2);

		//Exclusive merge 2
		EXCLUSIVE_MERGE_2.addChildAndForceParent(DP);

		//DP
		DP.addChildAndForceParent(EXCLUSIVE_SPLIT_1);

		//Exclusive split 1
		EXCLUSIVE_SPLIT_1.addChildAndForceParent(BCO);
		EXCLUSIVE_SPLIT_1.addChildAndForceParent(EXCLUSIVE_MERGE_5); //TODO A gérer mieux : se fait supprimer par la réduction transitive

		//BCO
		BCO.addChildAndForceParent(EXCLUSIVE_SPLIT_2);

		//Exclusive split 2
		EXCLUSIVE_SPLIT_2.addChildAndForceParent(FSDP);
		EXCLUSIVE_SPLIT_2.addChildAndForceParent(EXCLUSIVE_MERGE_3);
		EXCLUSIVE_SPLIT_2.addChildAndForceParent(EXCLUSIVE_MERGE_4);

		//Exclusive merge 3
		EXCLUSIVE_MERGE_3.addChildAndForceParent(TSDP);

		//Exclusive merge 4
		EXCLUSIVE_MERGE_4.addChildAndForceParent(SSDP);

		//FSDP
		FSDP.addChildAndForceParent(CFLR);

		//SSDP
		SSDP.addChildAndForceParent(CSLR);

		//TSDP
		TSDP.addChildAndForceParent(CTLR);

		//CFLR
		CFLR.addChildAndForceParent(EXCLUSIVE_SPLIT_3);

		//Exclusive split 3
		EXCLUSIVE_SPLIT_3.addChildAndForceParent(EXCLUSIVE_MERGE_4);
		EXCLUSIVE_SPLIT_3.addChildAndForceParent(EXCLUSIVE_MERGE_2);
		EXCLUSIVE_SPLIT_3.addChildAndForceParent(EXCLUSIVE_MERGE_5);

		//CSLR
		CSLR.addChildAndForceParent(EXCLUSIVE_SPLIT_4);

		//Exclusive split 4
		EXCLUSIVE_SPLIT_4.addChildAndForceParent(EXCLUSIVE_MERGE_3);
		EXCLUSIVE_SPLIT_4.addChildAndForceParent(EXCLUSIVE_MERGE_2);
		EXCLUSIVE_SPLIT_4.addChildAndForceParent(EXCLUSIVE_MERGE_5);

		//CTLR
		CTLR.addChildAndForceParent(EXCLUSIVE_SPLIT_5);

		//Exclusive split 5
		EXCLUSIVE_SPLIT_5.addChildAndForceParent(EXCLUSIVE_MERGE_2);
		EXCLUSIVE_SPLIT_5.addChildAndForceParent(EXCLUSIVE_MERGE_5);

		//Exclusive merge 5
		EXCLUSIVE_MERGE_5.addChildAndForceParent(RF);

		//RF
		RF.addChildAndForceParent(EXCLUSIVE_SPLIT_6);

		//Exclusive split 6
		EXCLUSIVE_SPLIT_6.addChildAndForceParent(ShFMS);
		EXCLUSIVE_SPLIT_6.addChildAndForceParent(EXCLUSIVE_MERGE_1);

		return exampleElements;
	}
}
