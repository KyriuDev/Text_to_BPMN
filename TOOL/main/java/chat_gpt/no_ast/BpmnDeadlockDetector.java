package chat_gpt.no_ast;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The deadlock detection is based on the semantics of BPMN and relies on a fixed point approach, in which
 * the behaviour of a process is considered to be (completely) known whenever its execution encounters an already
 * known state, that is, a state in which the number of circulating tokens and their position is identical to the
 * one of a previously reached state.
 * When such a state is reached, the process is asked to terminate.
 * If it cannot, then the process contains a deadlock.
 * If there is no such state and the process is not blocked, then the process is infinitely recursive (TODO: is this true?)
 * If there is no such state and the process is blocked, then the process contains a deadlock.
 * If the process can reach such a state and can terminate, then it is live.
 */
public class BpmnDeadlockDetector
{
	private static final int REPETITION_BOUND = 3; //Maximum number of encountering of any given task of the process (TODO try see whether 3 is sufficient or not)
	private static final Logger log = LoggerFactory.getLogger(BpmnDeadlockDetector.class);
	private final ArrayList<SimulationInfo> failySimulations;
	private final Graph graph;

	public BpmnDeadlockDetector(final Graph graph)
	{
		this.graph = graph;
		this.failySimulations = new ArrayList<>();
	}

	public ArrayList<SimulationInfo> detectDeadlocksAndLivelocks()
	{
		this.clear();
		final HashSet<Node> nodes = this.graph.toSet();
		final HashMap<Node, HashMap<Node, Integer>> executionFlow = new HashMap<>();

		for (Node node : nodes)
		{
			if (node.bpmnObject() instanceof Gateway
					&& node.parentNodes().size() > 1)
			{
				//Mark as merge gateway
				((Gateway) node.bpmnObject()).markAsMergeGateway();
			}

			final HashMap<Node, Integer> sourceTokens = new HashMap<>();

			for (Node parent : node.parentNodes())
			{
				sourceTokens.put(parent, 0);
			}

			executionFlow.put(node, sourceTokens);
		}

		final SimulationInfo initialSimulationInfo = new SimulationInfo(executionFlow);
		initialSimulationInfo.addTokenToNode(this.graph.initialNode(), null);
		initialSimulationInfo.increaseNodeOccurrence(this.graph.initialNode());
		initialSimulationInfo.computeCurrentConfigurationInformation();

		final ArrayList<SimulationInfo> simulationsToProceed = new ArrayList<>();
		simulationsToProceed.add(initialSimulationInfo);

		while (!simulationsToProceed.isEmpty())
		{
			//System.out.println("infinity");
			final ArrayList<SimulationInfo> nextSimulationsToProceed = new ArrayList<>();

			for (SimulationInfo simulationInfo : simulationsToProceed)
			{
				final ArrayList<SimulationInfo> nextSimulations = this.pushTokens(simulationInfo, false);
				nextSimulationsToProceed.addAll(nextSimulations);
				//this.verifyValidity(simulationInfo, nextSimulations);

				if (nextSimulations.isEmpty())
				{
					//If no simulation was produced by "pushTokens()", it might be because of a deadlock
					for (Node node : simulationInfo.tokensPerNode().keySet())
					{
						if (simulationInfo.nbTokensOf(node) > 0)
						{
							if (this.graph.endNodes().contains(node))
							{
								//System.out.println("Node \"" + node.bpmnObject().id() + "\" has " + simulationInfo.nbTokensOf(node) + " tokens but is an end node :-)");
							}
							else
							{
								//System.out.println("Faily simulation:\n" + finalSimulationInfo);
								System.out.println("Node \"" + node.bpmnObject().id() + "\" still has " + simulationInfo.nbTokensOf(node) + " tokens!");
								this.failySimulations.add(simulationInfo);
								simulationInfo.hasReachedFixPoint = true;
								//Return is used here to stop the computations, as we know that there is (at least) one reachable faily configuration.
								return this.failySimulations;
							}
						}
					}
				}
			}

			for (Iterator<SimulationInfo> iterator = nextSimulationsToProceed.iterator(); iterator.hasNext(); )
			{
				final SimulationInfo nextSimulation = iterator.next();
				//System.out.println("Next simulation: " + nextSimulation.toString());
				nextSimulation.computeCurrentConfigurationInformation();

				if (nextSimulation.hasReachedFixPoint())
				{
					iterator.remove();
					final SimulationInfo finalSimulationInfo = this.finaliseSimulationV2(nextSimulation);

					for (Node node : finalSimulationInfo.tokensPerNode().keySet())
					{
						if (finalSimulationInfo.nbTokensOf(node) > 0)
						{
							if (this.graph.endNodes().contains(node))
							{
								//System.out.println("Node \"" + node.bpmnObject().id() + "\" has " + finalSimulationInfo.nbTokensOf(node) + " tokens but is an end node :-)");
							}
							else
							{
								//System.out.println("Faily simulation:\n" + finalSimulationInfo);
								System.out.println("Node \"" + node.bpmnObject().id() + "\" still has " + finalSimulationInfo.nbTokensOf(node) + " tokens!");
								this.failySimulations.add(finalSimulationInfo);
								//Return is used here to stop the computations, as we know that there is (at least) one reachable faily configuration.
								return this.failySimulations;
							}
						}
					}
				}
				else if (nextSimulation.willNeverReachFixPoint())
				{
					System.out.println("Process is infinitely recursive :-(");
					iterator.remove();
					this.failySimulations.add(nextSimulation);
					return this.failySimulations;
				}
			}

			//System.out.println(simulationsToProceed.size() + " simulations to proceed.");
			//System.out.println(nextSimulationsToProceed.size() + " next simulations to proceed.");

			simulationsToProceed.clear();
			simulationsToProceed.addAll(nextSimulationsToProceed);
		}

		return this.failySimulations;
	}

	public boolean simulationEndedWithDeadlocksOrLivelocks()
	{
		return !this.failySimulations.isEmpty();
	}

	public ArrayList<SimulationInfo> getFailySimulations()
	{
		return this.failySimulations;
	}

	public void clear()
	{
		this.failySimulations.clear();
	}

	//Private methods

	/**
	 * This method is used to make a step forward in the simulation by forwarding all the forwardable tokens
	 * currently circulating.
	 * One step forward may lead to several possible executions in case of choices.
	 * They are voluntarily separated as we want to compute all the possible executions of the process.
	 *
	 * @param executionFlow    the simulation to make progress on
	 * @param forceTermination a boolean value indicating whether we want to force the termination of the simulation or not
	 * @return a list of possible executions resulting from the movement of the tokens one step forward
	 */
	private ArrayList<SimulationInfo> pushTokens(final SimulationInfo executionFlow,
												 final boolean forceTermination)
	{
		//System.out.println("Execution flow before push:\n" + executionFlow);

		final ArrayList<SimulationInfo> nextExecutionFlows = new ArrayList<>();
		final HashSet<Node> exclusiveSplits = new HashSet<>();
		final SimulationInfo modifiableExecutionFlow = executionFlow.copy();
		executionFlow.markAsNonModifiable();
		boolean changed = false;

		for (Node node : executionFlow.nodes())
		{
			final int nbTokens = executionFlow.nbTokensOf(node);

			if (nbTokens < 1
					|| !node.hasChildren()) continue;

			//We check whether we can transmit our token to our children
			if (node.bpmnObject().type() == BpmnProcessType.TASK)
			{
				changed = true;

				if (node.childNodes().size() > 1)
				{
					throw new RuntimeException("Task \"" + node.bpmnObject().name() + "\" has several outgoing flows!");
				}
				else
				{
					final Node child = node.childNodes().iterator().next();
					this.sendTokenToChild(node, child, modifiableExecutionFlow);
				}
			}
			else if (node.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY)
			{
				changed = true;

				final Gateway gateway = (Gateway) node.bpmnObject();

				if (gateway.isSplitGateway())
				{
					if (forceTermination)
					{
						final Node closestNodeToEnd = this.getClosestNodeToEnd(node.childNodes());
						this.sendTokenToChild(node, closestNodeToEnd, modifiableExecutionFlow);
					}
					else
					{
						exclusiveSplits.add(node);
					}
				}
				else
				{
					if (node.childNodes().size() > 1)
					{
						throw new RuntimeException("Exclusive merge gateway \"" + node.bpmnObject().id() + "\" has several outgoing flows!");
					}
					else
					{
						final Node child = node.childNodes().iterator().next();
						this.sendTokenToChild(node, child, modifiableExecutionFlow);
					}
				}
			}
			else if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
			{
				final Gateway gateway = (Gateway) node.bpmnObject();

				if (gateway.isSplitGateway())
				{
					changed = true;

					//If the parallel split holds several tokens => send all of them
					boolean occurrenceToIncrease = true;

					while (modifiableExecutionFlow.nodeHoldsTokens(node))
					{
						modifiableExecutionFlow.removeTokenFromNode(node);

						for (Node child : node.childNodes())
						{
							modifiableExecutionFlow.addTokenToNode(child, node);

							if (occurrenceToIncrease)
							{
								modifiableExecutionFlow.increaseNodeOccurrence(child);
							}
						}

						occurrenceToIncrease = false;
					}
				}
				else
				{
					if (modifiableExecutionFlow.parallelMergeIsReady(node))
					{
						changed = true;

						if (node.childNodes().size() > 1)
						{
							throw new RuntimeException("Parallel merge gateway \"" + node.bpmnObject().id() + "\" has several outgoing flows!");
						}
						else
						{
							final Node child = node.childNodes().iterator().next();
							modifiableExecutionFlow.increaseNodeOccurrence(child);

							//If the parallel merge holds several tokens => send all of them
							while (modifiableExecutionFlow.parallelMergeIsReady(node))
							{
								modifiableExecutionFlow.consumeParallelMergeTokens(node);
								modifiableExecutionFlow.addTokenToNode(child, node);
							}
						}
					}
				}
			}
			else if (node.bpmnObject().type() == BpmnProcessType.SEQUENCE_FLOW)
			{
				changed = true;
				final Node child = node.childNodes().iterator().next();
				this.sendTokenToChild(node, child, modifiableExecutionFlow);
			}
			else if (node.bpmnObject().type() == BpmnProcessType.START_EVENT)
			{
				changed = true;
				final Node child = node.childNodes().iterator().next();
				//System.out.println("Child type: " + child.bpmnObject().type().toString());
				this.sendTokenToChild(node, child, modifiableExecutionFlow);
				//System.out.println(executionFlow);
			}
			else
			{
				throw new RuntimeException("Type |" + node.bpmnObject().type() + "| is not supported!");
			}
		}

		if (changed)
		{
			nextExecutionFlows.add(modifiableExecutionFlow);
		}

		if (!forceTermination)
		{
			while (!exclusiveSplits.isEmpty())
			{
				//System.out.println(exclusiveSplits.size() + " exclusive splits.");
				final Node exclusiveSplit = exclusiveSplits.iterator().next();
				final ArrayList<SimulationInfo> tempResults = new ArrayList<>();

				for (SimulationInfo simulationInfo : nextExecutionFlows)
				{
					for (Node child : exclusiveSplit.childNodes())
					{
						final SimulationInfo simulationInfoCopy = simulationInfo.copy();
						tempResults.add(simulationInfoCopy);
						simulationInfoCopy.removeTokenFromNode(exclusiveSplit);
						simulationInfoCopy.addTokenToNode(child, exclusiveSplit);
						simulationInfoCopy.increaseNodeOccurrence(child);
					}

					if (simulationInfo.nbTokensOf(exclusiveSplit) == 1)
					{
						//The current exclusive split just sent its last token => remove it from the list of splits
						exclusiveSplits.remove(exclusiveSplit);
					}
				}

				nextExecutionFlows.clear();
				nextExecutionFlows.addAll(tempResults);
			}
		}

		int i = 1;

		/*System.out.println("Next execution flows:");

		for (SimulationInfo nextExecutionFlow : nextExecutionFlows)
		{
			System.out.println("- " + i++ + ": " + nextExecutionFlow);
		}*/

		return nextExecutionFlows;
	}

	private void sendTokenToChild(final Node node,
								  final Node child,
								  final SimulationInfo executionFlow)
	{
		executionFlow.increaseNodeOccurrence(child);

		while (executionFlow.nodeHoldsTokens(node))
		{
			executionFlow.removeTokenFromNode(node);
			executionFlow.addTokenToNode(child, node);
		}
	}

	private SimulationInfo finaliseSimulationV2(final SimulationInfo simulationInfo)
	{
		final SimulationInfo baseInfos = simulationInfo.copy();
		SimulationInfo currentSimulationInfo = simulationInfo;
		final ArrayList<SimulationInfo> nextSimulations = new ArrayList<>();

		//System.out.println("Base configuration:\n" + baseInfos.toString());

		do
		{
			//System.out.println("Before config:\n" + currentSimulationInfo.toString());

			nextSimulations.clear();
			nextSimulations.addAll(this.pushTokens(currentSimulationInfo, true));
			currentSimulationInfo = nextSimulations.isEmpty() ? currentSimulationInfo : nextSimulations.get(0);
			currentSimulationInfo.computeCurrentConfigurationInformation();

			//System.out.println("Next simulation is empty: " + nextSimulations.isEmpty());
			//System.out.println("After config:\n" + currentSimulationInfo.toString());
			//System.out.println("Current configuration is same as previous one: " + currentSimulationInfo.currentConfigurationIsSameAsPreviousOne());
		}
		while (!nextSimulations.isEmpty());

		//System.out.println("Configuration before last push:\n" + currentSimulationInfo);
		//final ArrayList<SimulationInfo> finalSimulations = this.pushTokens(currentSimulationInfo, true);
		//System.out.println("Final configuration:\n" + (finalSimulations.isEmpty() ? currentSimulationInfo : finalSimulations.get(0)));

		return currentSimulationInfo;
	}

	/**
	 * This method computes the shortest path from several end nodes up to one candidate node among the set of given
	 * candidates.
	 * The shortness is computed in terms of number of nodes to traverse
	 *
	 * @param candidates
	 * @return
	 */
	private Node getClosestNodeToEnd(final Collection<Node> candidates)
	{
		int minDistance = -1;
		Node bestCandidate = null;

		for (Node endNode : this.graph.endNodes())
		{
			for (Node candidate : candidates)
			{
				final int distance = endNode.getMinDistanceToAncestor(candidate);

				if (bestCandidate == null
						|| distance < minDistance)
				{
					minDistance = distance;
					bestCandidate = candidate;
				}
			}
		}

		if (bestCandidate == null) throw new RuntimeException();

		return bestCandidate;
	}

	private void verifyValidity(final SimulationInfo baseSimulation,
								final Collection<SimulationInfo> nextSimulations)
	{
		for (SimulationInfo nextSimulation : nextSimulations)
		{
			if (baseSimulation.tokensPerNode().equals(nextSimulation.tokensPerNode())) continue;

			//if (baseConfig == nextConfig) continue;

			for (Node node : nextSimulation.tokensPerNode().keySet())
			{
				final int nbTokens = nextSimulation.nbTokensOf(node);

				if (nbTokens > 0)
				{
					boolean parentHadAToken = false;

					for (Node parent : node.parentNodes())
					{
						final int nbTokensParent = baseSimulation.nbTokensOf(parent);

						if (nbTokensParent > 0)
						{
							parentHadAToken = true;
							break;
						}
					}

					if (!parentHadAToken)
					{
						//System.out.println("Previous config: " + baseConfig);
						//System.out.println("Next config: " + nextConfig);

						throw new RuntimeException("Node \"" + node.bpmnObject().name() + "\" holds a token but" +
								" none of its parents had one!!!!!");
					}
				}
			}
		}
	}

	//Sub-classes

	public static class SimulationInfo
	{
		private final HashMap<Node, HashMap<Node, Integer>> tokensPerNode;
		private final HashMap<Node, Integer> nodesOccurrences;
		private final ArrayList<HashMap<Node, HashMap<Node, Integer>>> alreadyKnownConfigurations;
		//private final HashSet<Integer> alreadyReachedConfigurations;
		private boolean hasReachedFixPoint;
		private boolean willNeverReachFixPoint;
		private boolean modifiable;

		SimulationInfo()
		{
			this.tokensPerNode = new HashMap<>();
			this.nodesOccurrences = new HashMap<>();
			this.alreadyKnownConfigurations = new ArrayList<>();
			//this.alreadyReachedConfigurations = new HashSet<>();
			this.hasReachedFixPoint = false;
			this.willNeverReachFixPoint = false;
			this.modifiable = true;
		}

		SimulationInfo(final HashMap<Node, HashMap<Node, Integer>> tokensPerNode)
		{
			this.tokensPerNode = new HashMap<>(tokensPerNode);
			this.nodesOccurrences = new HashMap<>();
			this.alreadyKnownConfigurations = new ArrayList<>();
			//this.alreadyReachedConfigurations = new HashSet<>();
			this.hasReachedFixPoint = false;
			this.willNeverReachFixPoint = false;
			this.modifiable = true;
		}

		SimulationInfo(final SimulationInfo simulationInfo)
		{
			this.tokensPerNode = simulationInfo.copyTokensPerNode();
			this.alreadyKnownConfigurations = new ArrayList<>(simulationInfo.alreadyKnownConfigurations());
			this.nodesOccurrences = new HashMap<>(simulationInfo.nodesOccurrences());
			//this.alreadyReachedConfigurations = new HashSet<>(simulationInfo.alreadyReachedConfigurations());
			this.hasReachedFixPoint = simulationInfo.hasReachedFixPoint();
			this.willNeverReachFixPoint = simulationInfo.willNeverReachFixPoint();
			this.modifiable = true;
			//this.modifiable = simulationInfo.isModifiable();
		}

		public boolean hasLivelock()
		{
			return this.willNeverReachFixPoint;
		}

		public boolean hasDeadlock()
		{
			return this.hasReachedFixPoint;
		}

		public ArrayList<HashMap<Node, HashMap<Node, Integer>>> alreadyKnownConfigurations()
		{
			return this.alreadyKnownConfigurations;
		}

		Set<Node> nodes()
		{
			if (this.modifiable)
			{
				return this.tokensPerNode.keySet();
			}
			else
			{
				return Collections.unmodifiableSet(this.tokensPerNode.keySet());
			}
		}

		Node getNodeOfId(final String id)
		{
			for (Node node : this.tokensPerNode.keySet())
			{
				if (node.bpmnObject().id().equals(id))
				{
					return node;
				}
			}

			throw new RuntimeException();
		}

		boolean nodeHoldsTokens(final Node node)
		{
			return this.nbTokensOf(node) > 0;
		}

		int nbTokensOf(final Node node)
		{
			int nbTokens = 0;

			for (Node source : this.tokensPerNode.get(node).keySet())
			{
				nbTokens += this.tokensPerNode.get(node).get(source);
			}

			return nbTokens;
		}

		void addTokenToNode(final Node node,
							final Node source)
		{
			if (!this.isModifiable())
			{
				throw new RuntimeException("Current simulation information is not modifiable!");
			}

			if (!this.tokensPerNode.containsKey(node))
			{
				throw new RuntimeException("Node \"" + node.bpmnObject().name() + "\" not found in the tokens list!");
			}

			/*if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
				&& ((Gateway) node.bpmnObject()).isMergeGateway())
			{
				throw new RuntimeException("Parallel merge gateways should never hold tokens!");
			}*/

			final int currentNbTokens = this.tokensPerNode.get(node).getOrDefault(source, 0);
			this.tokensPerNode.get(node).put(source, currentNbTokens + 1);
		}

		void removeTokenFromNode(final Node node)
		{
			if (!this.isModifiable())
			{
				throw new RuntimeException("Current simulation information is not modifiable!");
			}

			if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
					&& ((Gateway) node.bpmnObject()).isMergeGateway())
			{
				//We remove a token from all sources
				for (Node source : node.parentNodes())
				{
					final int nbTokens = this.tokensPerNode.get(node).getOrDefault(source, 0);

					if (nbTokens < 1)
					{
						throw new RuntimeException("Node \"" + node.bpmnObject().name() + "\" is a triggered parallel merge" +
								" but it does not have all necessary tokens (at least no token from node \"" + source.bpmnObject().name() + "\")!");
					}

					this.tokensPerNode.get(node).put(source, nbTokens - 1);
				}
			}
			else
			{
				//We remove a token from any of the source
				boolean tokenRemoved = false;

				for (Node source : this.tokensPerNode.get(node).keySet())
				{
					final int nbTokensSource = this.tokensPerNode.get(node).getOrDefault(source, 0);

					if (nbTokensSource > 0)
					{
						tokenRemoved = true;
						this.tokensPerNode.get(node).put(source, nbTokensSource - 1);
						break;
					}
				}

				if (!tokenRemoved)
				{
					throw new RuntimeException("Node \"" + node.bpmnObject().name() + "\" has 0 token but is expected to send one!");
				}
			}
		}

		void consumeParallelMergeTokens(final Node parallelMerge)
		{
			if (!this.isModifiable())
			{
				throw new RuntimeException("Current simulation information is not modifiable!");
			}

			if (parallelMerge.bpmnObject().type() != BpmnProcessType.PARALLEL_GATEWAY
					|| !((Gateway) parallelMerge.bpmnObject()).isMergeGateway())
			{
				throw new RuntimeException("Node \"" + parallelMerge.bpmnObject().id() + "\" is not a parallel merge!");
			}

			for (Node parent : parallelMerge.parentNodes())
			{
				final int nbTokensParent = this.tokensPerNode.get(parallelMerge).getOrDefault(parent, 0);

				if (nbTokensParent < 1)
				{
					throw new RuntimeException("Parallel merge gateway \"" + parallelMerge.bpmnObject().name() + "\"" +
							" did not receive any token from source \"" + parent.bpmnObject().name() + "\" but has been" +
							" asked for consumption!");
				}

				this.tokensPerNode.get(parallelMerge).put(parent, nbTokensParent - 1);
			}
		}

		boolean parallelMergeIsReady(final Node parallelMerge)
		{
			final HashMap<Node, Integer> parallelMergeTokens = this.tokensPerNode.get(parallelMerge);

			for (Node parent : parallelMerge.parentNodes())
			{
				final int nbTokensFromParent = parallelMergeTokens.getOrDefault(parent, 0);

				if (nbTokensFromParent < 1)
				{
					return false;
				}
			}

			return true;
		}

		boolean parallelMergeIsReadyOnlyOnce(final Node parallelMerge)
		{
			final HashMap<Node, Integer> parallelMergeTokens = this.tokensPerNode.get(parallelMerge);

			for (Node parent : parallelMerge.parentNodes())
			{
				final int nbTokensFromParent = parallelMergeTokens.getOrDefault(parent, 0);

				if (nbTokensFromParent == 1)
				{
					return true;
				}
			}

			return false;
		}

		Map<Node, HashMap<Node, Integer>> tokensPerNode()
		{
			return this.isModifiable() ? this.tokensPerNode : Collections.unmodifiableMap(this.tokensPerNode);
		}

		Map<Node, Integer> nodesOccurrences()
		{
			return this.isModifiable() ? this.nodesOccurrences : Collections.unmodifiableMap(this.nodesOccurrences);
		}

		/*Set<Integer> alreadyReachedConfigurations()
		{
			return this.isModifiable() ? this.alreadyReachedConfigurations : Collections.unmodifiableSet(this.alreadyReachedConfigurations);
		}*/

		boolean hasReachedFixPoint()
		{
			return this.hasReachedFixPoint;
		}

		boolean willNeverReachFixPoint()
		{
			return this.willNeverReachFixPoint;
		}

		boolean isModifiable()
		{
			return this.modifiable;
		}

		void markAsNonModifiable()
		{
			this.modifiable = false;
		}

		void increaseNodeOccurrence(final Node node)
		{
			if (!this.isModifiable())
			{
				throw new RuntimeException("Current simulation information is not modifiable!");
			}

			final int nbOccurrence = this.nodesOccurrences.getOrDefault(node, 0);
			//System.out.println("Occurrence of node \"" + node.bpmnObject().id() + "\" reached " + (nbOccurrence + 1));
			this.nodesOccurrences.put(node, nbOccurrence + 1);
		}

		void computeCurrentConfigurationInformation()
		{
			if (this.configurationIsKnown())
			{
				//System.out.println("System reached fix point");
				this.hasReachedFixPoint = true;
			}
			else
			{
				this.alreadyKnownConfigurations.add(this.copyTokensPerNode());

				if (this.nodesOccurrences.containsValue(REPETITION_BOUND))
				{
					//System.out.println("System is infinitely recursive");
					this.willNeverReachFixPoint = true;
				}
			}
		}

		boolean currentConfigurationIsSameAsPreviousOne()
		{
			if (this.alreadyKnownConfigurations.isEmpty())
			{
				System.out.println("No sufficient configs");
				return false;
			}

			final HashMap<Node, HashMap<Node, Integer>> lastConfiguration = this.tokensPerNode;
			final HashMap<Node, HashMap<Node, Integer>> beforeLastConfiguration = this.alreadyKnownConfigurations.get(this.alreadyKnownConfigurations.size() - 1);

			for (Node beforeLastConfigNode : beforeLastConfiguration.keySet())
			{
				if (!lastConfiguration.containsKey(beforeLastConfigNode))
				{
					System.out.println("Last configuration misses node \"" + beforeLastConfigNode.bpmnObject().id() + "\".");
				}

				final HashMap<Node, Integer> lastConfig = lastConfiguration.get(beforeLastConfigNode);
				final HashMap<Node, Integer> beforeLastConfig = beforeLastConfiguration.get(beforeLastConfigNode);

				for (Node sourceBeforeLastConfigNode : beforeLastConfig.keySet())
				{
					if (!lastConfig.containsKey(sourceBeforeLastConfigNode))
					{
						System.out.println("Last configuration node \"" + beforeLastConfigNode.bpmnObject().id() + "\" misses token(s) from source node \"" + sourceBeforeLastConfigNode.bpmnObject().id() + "\".");
					}

					final int beforeTokens = beforeLastConfig.get(sourceBeforeLastConfigNode);
					final int afterTokens = lastConfig.get(sourceBeforeLastConfigNode);

					if (beforeTokens != afterTokens)
					{
						System.out.println("Last and before last configurations do not have the same number of tokens for source \"" + sourceBeforeLastConfigNode.bpmnObject().id() + "\" (B=" + beforeTokens + "/A=" + afterTokens + ").");
					}
				}
			}

			//System.out.println("Last configuration: " + lastConfiguration);
			//System.out.println("Before last configuration: " + beforeLastConfiguration);

			return lastConfiguration.equals(beforeLastConfiguration);
		}

		boolean configurationIsKnown()
		{
			final boolean result1 = this.alreadyKnownConfigurations.contains(this.tokensPerNode);
			/*final boolean result2 = this.myConfigurationIsKnown();

			if (result1 != result2) throw new RuntimeException();*/

			return result1;
		}

		boolean myConfigurationIsKnown()
		{
			boolean configIsKnown = false;

			for (HashMap<Node, HashMap<Node, Integer>> knownConfiguraton : this.alreadyKnownConfigurations)
			{
				boolean configMatches = true;

				if (!knownConfiguraton.keySet().equals(this.tokensPerNode.keySet())) throw new RuntimeException();

				for (Node node : knownConfiguraton.keySet())
				{
					final HashMap<Node, Integer> existingSources = knownConfiguraton.get(node);
					final HashMap<Node, Integer> currentSources = this.tokensPerNode.get(node);

					if (!existingSources.keySet().equals(currentSources.keySet())) throw new RuntimeException();

					for (Node source : existingSources.keySet())
					{
						final int existingNbTokens = existingSources.get(source);
						final int currentNbTokens = currentSources.get(source);

						if (existingNbTokens != currentNbTokens)
						{
							configMatches = false;
							break;
						}
					}

					if (!configMatches)
					{
						break;
					}
				}

				if (configMatches)
				{
					configIsKnown = true;
					break;
				}
			}

			return configIsKnown;
		}

		Node getNonTriggerableMergeIfAny()
		{
			for (Node node : this.tokensPerNode.keySet())
			{
				if (node.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
						&& ((Gateway) node.bpmnObject()).isMergeGateway())
				{
					if (this.nbTokensOf(node) != 0
							&& !this.parallelMergeIsReady(node))
					{
						return node;
					}
				}
			}

			//No parallel merge gateway has a token
			return null;
		}

		SimulationInfo copy()
		{
			return new SimulationInfo(this);
		}

		HashMap<Node, HashMap<Node, Integer>> copyTokensPerNode()
		{
			final HashMap<Node, HashMap<Node, Integer>> copy = new HashMap<>();

			for (Node node : this.tokensPerNode.keySet())
			{
				copy.put(node, new HashMap<>(this.tokensPerNode.get(node)));
			}

			return copy;
		}

		@Override
		public String toString()
		{
			final StringBuilder builder = new StringBuilder("The current simulation has the following information:\n");

			for (Node node : this.tokensPerNode.keySet())
			{
				final int nbTokens = this.nbTokensOf(node);

				if (nbTokens < 0)
					throw new RuntimeException("Node \"" + node.bpmnObject().id() + "\" has a negative number of tokens (" + nbTokens + ")!!!!");

				if (nbTokens != 0)
				{
					builder.append("	- Node \"")
							.append(node.bpmnObject().id())
							.append("\" has ")
							.append(nbTokens)
							.append(" token(s).\n");
				}
			}

			return builder.toString();
		}
	}
}
