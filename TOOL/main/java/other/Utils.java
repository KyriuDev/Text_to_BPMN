package other;

import bpmn.graph.Node;
import chat_gpt.ast_management.AbstractSyntaxTree;
import constants.PrintLevel;
import exceptions.ExceptionStatus;
import exceptions.ExpectedException;
import refactoring.legacy.dependencies.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

import static main.Main.PRINT_LEVEL;

public class Utils
{
    private static final long MICROSECONDS_THRESHOLD = 1000;
    private static final long MILLISECONDS_THRESHOLD = 1000000;
    private static final long SECONDS_THRESHOLD = 1000000000;
    private static final long MINUTES_THRESHOLD = 60000000000L;
    private static final long HOURS_THRESHOLD = 3600000000000L;
    private static final long DAYS_THRESHOLD = 86400000000000L;

    private Utils()
    {

    }

    public static String nanoSecToReadable(final long nanoseconds)
    {
        final DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.CEILING);

        if (nanoseconds > DAYS_THRESHOLD)
        {
            return df.format((double) nanoseconds / (double) DAYS_THRESHOLD) + " days";
        }
        else if (nanoseconds > HOURS_THRESHOLD)
        {
            return df.format((double) nanoseconds / (double) HOURS_THRESHOLD) + "h";
        }
        else if (nanoseconds > MINUTES_THRESHOLD)
        {
            return df.format((double) nanoseconds / (double) MINUTES_THRESHOLD) + "m";
        }
        else if (nanoseconds > SECONDS_THRESHOLD)
        {
            //More than 1sec
            return df.format((double) nanoseconds / (double) SECONDS_THRESHOLD) + "s";
        }
        else if (nanoseconds > MILLISECONDS_THRESHOLD)
        {
            //More than 1ms
            return df.format((double) nanoseconds / (double) MILLISECONDS_THRESHOLD) + "ms";
        }
        else if (nanoseconds > MICROSECONDS_THRESHOLD)
        {
            //More than 1µs
            return df.format((double) nanoseconds / (double) MICROSECONDS_THRESHOLD) + "µs";
        }
        else
        {
            //Value in nanoseconds
            return df.format((double) nanoseconds) + "ns";
        }
    }

    public static String join(final List<?> elements,
                              final String separatorToUse)
    {
        final StringBuilder builder = new StringBuilder();
        String separator = "";

        for (Object element : elements)
        {
            builder.append(separator)
                    .append(element.toString());

            separator = separatorToUse;
        }

        return builder.toString();
    }

    public static String protectString(String s)
    {
        return s.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("&", "&amp;");
    }

    public static String quoteString(String s)
    {
        return "\"" + s + "\"";
    }

    public static String quoteString(int i)
    {
        return "\"" + i + "\"";
    }

    public static boolean isAnInt(final String s)
    {
        try
        {
            Integer.parseInt(s);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static boolean isAnInt(final char c)
    {
        try
        {
            Integer.parseInt(String.valueOf(c));
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static String generateRandomIdentifier()
    {
        return Utils.generateRandomIdentifier(30);
    }

    public static String generateRandomIdentifier(final int length)
    {
        final StringBuilder builder = new StringBuilder();
        final Random random = new Random();

        for (int i = 0; i < length; i++)
        {
            final char c;

            //CAPS
            if (random.nextBoolean())
            {
                c = (char) (random.nextInt(25) + 65 + 1); //Exclusive upper bound
            }
            //NON CAPS
            else
            {
                c = (char) (random.nextInt(25) + 97 + 1); //Exclusive upper bound
            }

            builder.append(c);
        }

        return builder.toString();
    }

    public static int max(final Collection<Integer> integers)
    {
        int max = Integer.MIN_VALUE;

        if (integers.isEmpty())
        {
            return max;
        }

        for (Integer integer : integers)
        {
            if (integer > max)
            {
                max = integer;
            }
        }

        return max;
    }

    public static int min(final Collection<Integer> integers)
    {
        int min = Integer.MAX_VALUE;

        if (integers.isEmpty())
        {
            return min;
        }

        for (Integer integer : integers)
        {
            if (integer < min)
            {
                min = integer;
            }
        }

        return min;
    }

    public static String printPaths(final Collection<ArrayList<Node>> paths)
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("The following paths were found:\n");

        for (ArrayList<Node> path : paths)
        {
            builder.append("     - ");

            for (Node node : path)
            {
                builder.append(node.bpmnObject().id())
                        .append(" - ");
            }

            builder.append("\n");
        }

        return builder.toString();
    }

    public static <T> Collection<T> getIntersectionOf(final Collection<T> collection1,
                                                      final Collection<T> collection2)
    {
        final Collection<T> intersection = new HashSet<>();

        for (T element1 : collection1)
        {
            if (collection2.contains(element1))
            {
                intersection.add(element1);
            }
        }

        return intersection;
    }

    public static <T> HashMap<Integer, Collection<Collection<T>>> getSortedCombinationsOf(final Collection<T> elements)
    {
        final HashMap<Integer, Collection<Collection<T>>> sortedCombinations = new HashMap<>();
        final Collection<Collection<T>> combinations = Utils.getCombinationsOf(elements);

        for (Collection<T> combination : combinations)
        {
            final Collection<Collection<T>> adequateCollection = sortedCombinations.computeIfAbsent(combination.size(), a -> new ArrayList<>());
            adequateCollection.add(combination);
        }

        return sortedCombinations;
    }

    /**
     * In this method, we want to compute all the combinations of elements of a set.
     * For example, getCombinationsOf([1,2,3]) returns [[1], [2], [3], [1,2], [1,3],
     * [2,3], [1,2,3]].
     *
     * @param elements the elements to combine
     * @return the list of all possible combinations of the elements
     * @param <T> any type
     */
    public static <T> Collection<Collection<T>> getCombinationsOf(final Collection<T> elements)
    {
        return Utils.getCombinationsOf(elements, -1);
    }

    /**
     * In this method, we want to compute all the combinations of elements of a set.
     * For example, getCombinationsOf([1,2,3]) returns [[1], [2], [3], [1,2], [1,3],
     * [2,3], [1,2,3]].
     *
     * @param elements the elements to combine
     * @return the list of all possible combinations of the elements
     * @param <T> any type
     */
    public static <T> Collection<Collection<T>> getCombinationsOf(final Collection<T> elements,
                                                                  final int combinationSize)
    {
        final Collection<Collection<T>> combinations = new ArrayList<>();
        final ArrayList<T> sortedElements = new ArrayList<>(elements);

        Utils.getCombinationsOf(combinations, new ArrayList<>(), sortedElements, combinationSize);

        return combinations;
    }

    /**
     * In this method, we want all the combinations of the elements of the list respecting
     * the order in which they are in the list, meaning that if we have elements [1,2,3],
     * the combination [1,3] is not possible because it does not respect the order for 2.
     *
     * @param elements the elements to combine
     * @return the list of all ordered combinations
     * @param <T> any element
     */
    public static <T> List<List<T>> getOrderedCombinationsOf(List<T> elements)
    {
        final List<List<T>> combinations = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++)
        {
            for (int j = i; j < elements.size(); j++)
            {
                final List<T> combination = new ArrayList<>();

                for (int index = i; index <= j; index++)
                {
                    combination.add(elements.get(index));
                }

                combinations.add(combination);
            }
        }

        return combinations;
    }

    /**
     * In this method, we want to compute the cartesian product of the elements passed as argument.
     * For example, collection [[1,2], [3,4]] will have the following output: [[1,3], [1,4], [2,3], [2,4]].
     *
     * @param elements the elements to combine
     * @return the collection corresponding to the cartesian product of the original elements
     * @param <T> any element
     */
    public static <T> List<List<T>> getCartesianProductOf(List<List<T>> elements)
    {
        if (elements.size() < 2) throw new IllegalArgumentException("Cannot generate the product of one set only.");

        return getCartesianProductOf(0, elements);
    }

    //Private methods

    private static <T> List<List<T>> getCartesianProductOf(int index,
                                                           List<List<T>> elements)
    {
        List<List<T>> ret = new ArrayList<>();

        if (index == elements.size())
        {
            ret.add(new ArrayList<>());
        }
        else
        {
            for (T element : elements.get(index))
            {
                for (List<T> set : getCartesianProductOf(index + 1, elements))
                {
                    set.add(element);
                    ret.add(set);
                }
            }
        }

        return ret;
    }

    private static <T> void getCombinationsOf(Collection<Collection<T>> allCombinations,
                                              Collection<T> currentCombination,
                                              List<T> remainingElements,
                                              int maxCombinationSize)
    {
        if (remainingElements.isEmpty())
        {
            return;
        }

        if (maxCombinationSize != -1
            && currentCombination.size() >= maxCombinationSize) return;

        for (int i = 0; i < remainingElements.size(); i++)
        {
            final List<T> newRemainingElements = new ArrayList<>(remainingElements);

            int toRemove = 0;

            //Avoid duplicates
            while (toRemove < i)
            {
                newRemainingElements.remove(0);
                toRemove++;
            }

            final List<T> currentCombinationCopy = new ArrayList<>(currentCombination);
            currentCombinationCopy.add(newRemainingElements.remove(0));
            allCombinations.add(currentCombinationCopy);

            Utils.getCombinationsOf(allCombinations, currentCombinationCopy, newRemainingElements, maxCombinationSize);
        }
    }

    /**
     * This method splits a set of dependencies into (potentially multiple) connected sets
     * of dependencies (i.e., sets from which a dependency graph can be generated)
     * @param rawDependencies the merged set of dependencies
     * @return a list of sets of connected dependencies
     */
    public static ArrayList<HashSet<Dependency>> splitDependencies(final HashSet<Dependency> rawDependencies)
    {
        //Verification
        if (rawDependencies.isEmpty()) return new ArrayList<>();

        //Initialisation
        final ArrayList<HashSet<Dependency>> finalDependencySets = new ArrayList<>();
        final HashSet<Dependency> initialSet = new HashSet<>();
        finalDependencySets.add(initialSet);
        final Iterator<Dependency> initiatingIterator = rawDependencies.iterator();
        initialSet.add(initiatingIterator.next());
        initiatingIterator.remove();

        //Splitting
        int previousNumberOfDependencies = rawDependencies.size();

        while (!rawDependencies.isEmpty())
        {
            for (Iterator<Dependency> iterator = rawDependencies.iterator(); iterator.hasNext(); )
            {
                final Dependency currentDependency = iterator.next();
                boolean shouldBreak = false;

                for (HashSet<Dependency> currentFinalSet : finalDependencySets)
                {
                    for (Dependency wellPlacedDependency : currentFinalSet)
                    {
                        if (currentDependency.firstNode().equals(wellPlacedDependency.firstNode())
                                || currentDependency.secondNode().equals(wellPlacedDependency.firstNode())
                                || currentDependency.firstNode().equals(wellPlacedDependency.secondNode())
                                || currentDependency.secondNode().equals(wellPlacedDependency.secondNode()))
                        {
                            currentFinalSet.add(currentDependency);
                            iterator.remove();
                            shouldBreak = true;
                            break;
                        }
                    }

                    if (shouldBreak)
                    {
                        break;
                    }
                }
            }

            if (previousNumberOfDependencies == rawDependencies.size())
            {
                //We did not remove any non-classified dependency during this iteration --> they are all independent of the currently existing sets
                final HashSet<Dependency> newSet = new HashSet<>();
                finalDependencySets.add(newSet);
                final Iterator<Dependency> nextIterator = rawDependencies.iterator();
                newSet.add(nextIterator.next());
                nextIterator.remove();
            }

            previousNumberOfDependencies = rawDependencies.size();
        }

        return finalDependencySets;
    }

    /**
     * This method generates a dependency graph from a set of dependencies.
     * For the method to execute properly (i.e., no infinite looping), each dependency must
     * be connected to (at least) another dependency.
     * For example, the set {(A,B), (C,D)} is not valid because dependencies (A,B) and (C,D)
     * are disjoint.
     * Oppositely, the set {(A,B), (B,C), (C,D)} is a valid set.
     *
     * @param dependencies the dependencies in couple format
     * @return the dependency graph corresponding to the given dependencies
     */
    public static DependencyGraph buildDependencyGraph(final HashSet<Dependency> dependencies) throws ExpectedException
    {
        return Utils.buildDependencyGraph(dependencies, false).second();
    }

    /**
     * This method generates a dependency graph from a set of dependencies.
     * For the method to execute properly (i.e., no infinite looping), each dependency must
     * be connected to (at least) another dependency.
     * For example, the set {(A,B), (C,D)} is not valid because dependencies (A,B) and (C,D)
     * are disjoint.
     * Oppositely, the set {(A,B), (B,C), (C,D)} is a valid set.
     *
     * @param dependencies the dependencies in couple format
     * @return the dependency graph corresponding to the given dependencies
     */
    public static Pair<HashSet<AbstractSyntaxTree>, DependencyGraph> buildDependencyGraph(final HashSet<Dependency> dependencies,
                                                                                          final boolean allowLoops) throws ExpectedException
    {
        if (PRINT_LEVEL >= PrintLevel.PRINT_ALL)
        {
            for (Dependency dependency : dependencies)
            {
                System.out.println("(" + dependency.firstNode().bpmnObject().id() + "," + dependency.secondNode().bpmnObject().id() + ")");
            }
        }

        final DependencyGraph dependencyGraph = new DependencyGraph();

        if (dependencies.isEmpty())
        {
            throw new ExpectedException("No dependency were given.", ExceptionStatus.CONTRADICTORY_VALUES);
        }

		/*if (dependencies.size() == 1)
		{
			final Dependency dependency = dependencies.iterator().next();

			if (dependency.firstNode().equals(DUMMY_NODE)
				|| dependency.secondNode().equals(DUMMY_NODE))
			{
				//We have and ad-hoc dependency
				dependencyGraph.addInitialNode(new Node(dependency.firstNode().equals(DUMMY_NODE) ? dependency.secondNode().bpmnObject() : dependency.firstNode().bpmnObject()));
				return dependencyGraph;
			}
		}*/

        final HashMap<Node, HashSet<Node>> brokenCycles = new HashMap<>();
        HashSet<Dependency> oldDependencies = new HashSet<>();

        while (!dependencies.isEmpty())
        {
            //System.out.println("oops");

            if (oldDependencies.equals(dependencies))
            {
                throw new ExpectedException("Infinite looping detected and avoided.", ExceptionStatus.CONTRADICTORY_VALUES);
            }

            oldDependencies.clear();
            oldDependencies.addAll(dependencies);

            for (Iterator<Dependency> iterator = dependencies.iterator(); iterator.hasNext(); )
            {
                final Dependency dependency = iterator.next();
                final Node firstNode = new Node(dependency.firstNode().bpmnObject());
                final Node secondNode = new Node(dependency.secondNode().bpmnObject());

                if (dependencyGraph.hasNode(firstNode)
                    && dependencyGraph.hasNode(secondNode))
                {
                    //Both nodes are already in the graph: connect them together and remove the second from the initial nodes
                    final Node firstNodeInGraph = dependencyGraph.getNodeFromID(firstNode.bpmnObject().id());
                    final Node secondNodeInGraph = dependencyGraph.getNodeFromID(secondNode.bpmnObject().id());
                    firstNodeInGraph.addChild(secondNodeInGraph);
                    secondNodeInGraph.addParent(firstNodeInGraph);

                    if (dependencyGraph.hasLoops())
                    {
                        if (!allowLoops)
                        {
                             throw new ExpectedException("Dependency (" + firstNode.bpmnObject().id() + "," +
                                secondNode.bpmnObject().id() + ") generates a loop in the dependency graph!", ExceptionStatus.CONTRADICTORY_VALUES);
                        }

                        //We break the loops for the moment
                        final HashSet<Node> hashSet = brokenCycles.computeIfAbsent(firstNodeInGraph, h -> new HashSet<>());
                        hashSet.add(secondNodeInGraph);
                        firstNodeInGraph.removeChildren(secondNodeInGraph);
                        secondNodeInGraph.removeParent(firstNodeInGraph);
                    }

                    //dependencyGraph.removeInitialNode(secondNodeInGraph);
                    iterator.remove();
                }
                else if (dependencyGraph.hasNode(firstNode))
                {
                    //First node is already in graph: connect the second one to it
                    final Node firstNodeInGraph = dependencyGraph.getNodeFromID(firstNode.bpmnObject().id());
                    firstNodeInGraph.addChild(secondNode);
                    secondNode.addParent(firstNodeInGraph);

                    iterator.remove();
                }
                else if (dependencyGraph.hasNode(secondNode))
                {
                    //Second node is already in graph: connect the first node to it, remove it from the initial nodes, and mark the first node as initial node
                    final Node secondNodeInGraph = dependencyGraph.getNodeFromID(secondNode.bpmnObject().id());
                    firstNode.addChild(secondNodeInGraph);
                    secondNodeInGraph.addParent(firstNode);
                    //dependencyGraph.removeInitialNode(secondNodeInGraph);
                    dependencyGraph.addInitialNode(firstNode);

                    iterator.remove();
                }
                else
                {
                    if (dependencyGraph.isEmpty())
                    {
                        dependencyGraph.addInitialNode(firstNode);
                        firstNode.addChild(secondNode);
                        secondNode.addParent(firstNode);

                        iterator.remove();
                    }
                }
            }
        }

        if (dependencyGraph.isEmpty())
        {
            throw new IllegalStateException();
        }

        System.out.println("Dependency graph before initial nodes removal:\n\n" + dependencyGraph.stringify(0));
        dependencyGraph.cleanInitialNodes();

        //Reduce an acyclic version of the graph
        dependencyGraph.reduceAcyclicGraph();

        //Put cycles back
        for (Node key : brokenCycles.keySet())
        {
            for (Node value : brokenCycles.get(key))
            {
                key.addChild(value);
                value.addParent(key);
            }
        }

        //Reduce the cyclic version
        final HashSet<AbstractSyntaxTree> newConstraints = dependencyGraph.reduce();

        dependencyGraph.cleanInitialNodes();

        return new Pair<>(newConstraints, dependencyGraph);
    }

    public static boolean isALetter(final char c)
    {
        return c >= 65 && c <= 122;
    }

    public static boolean intersectionIsNotEmpty(final Collection<?> set1,
                                                 final Collection<?> set2)
    {
        for (Object element1 : set1)
        {
            for (Object element2 : set2)
            {
                if (element1.equals(element2)) return true;
            }
        }

        return false;
    }

    public static boolean integersAreConsecutive(Collection<Integer> indices)
    {
        final ArrayList<Integer> sortedIndices = new ArrayList<>(indices);
        Collections.sort(sortedIndices);

        for (int i = 0; i < sortedIndices.size() - 1; i++)
        {
            final int currentIndex = sortedIndices.get(i);
            final int nextIndex = sortedIndices.get(i + 1);

            if (currentIndex != nextIndex - 1) return false;
        }

        return true;
    }

    public static String convertMonth(final int month)
    {
        switch (month)
        {
            case 0:
                return "January";

            case 1:
                return "February";

            case 2:
                return "March";

            case 3:
                return "April";

            case 4:
                return "May";

            case 5:
                return "June";

            case 6:
                return "July";

            case 7:
                return "August";

            case 8:
                return "September";

            case 9:
                return "October";

            case 10:
                return "November";

            case 11:
                return "December";

            default:
                throw new UnsupportedOperationException("Months indices should be between 0 and 11. Got " + month + " instead.");
        }
    }

    public static String getStackTrace(final Throwable throwable)
    {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static boolean setsAreEqual(final Set<Set<Node>> sets)
    {
        final Iterator<Set<Node>> iterator = sets.iterator();
        final Set<Node> pivot = iterator.next();

        while (iterator.hasNext())
        {
            final Set<Node> currentSet = iterator.next();

            if (!currentSet.equals(pivot)) return false;
        }

        return true;
    }
}
