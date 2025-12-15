package chat_gpt.ast_management.ease;

import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class Path<T>
{
	private final ArrayList<T> elements;
	private final HashSet<T> unsortedElements;
	private boolean isInGraph;

	public Path()
	{
		this.elements = new ArrayList<>();
		this.unsortedElements = new HashSet<>();
		this.isInGraph = false;
	}

	public int length()
	{
		return this.elements.size();
	}

	public boolean isEmpty()
	{
		return this.elements.isEmpty();
	}

	public boolean isInGraph()
	{
		return this.isInGraph;
	}

	public void markAsInGraph()
	{
		this.isInGraph = true;
	}

	public void addElementAtPosition(final int position,
									 final T element)
	{
		this.elements.add(position, element);
	}

	public void replaceElementAtPosition(final int position,
										 final T element)
	{
		this.elements.set(position, element);
	}

	public void add(final T element)
	{
		this.elements.add(element);
		this.unsortedElements.add(element);
	}

	public void addElements(final Collection<T> elements)
	{
		this.elements.addAll(elements);
		this.unsortedElements.addAll(elements);
	}

	public void removeElement(final T element)
	{
		this.elements.remove(element);

		if (!this.elements.contains(element))
		{
			this.unsortedElements.remove(element);
		}
	}

	public void removeAll(final T element)
	{
		while (this.elements.contains(element))
		{
			this.elements.remove(element);
		}

		this.unsortedElements.remove(element);
	}

	public T removeElement(final int index)
	{
		if (index < 0
			|| index > this.elements.size() - 1)
		{
			throw new IndexOutOfBoundsException();
		}

		final T removedElement = this.elements.remove(index);

		if (!this.elements.contains(removedElement))
		{
			this.unsortedElements.remove(removedElement);
		}

		return removedElement;
	}

	public T removeFirst()
	{
		if (this.elements.isEmpty())
		{
			throw new IllegalStateException();
		}

		final T removedElement = this.elements.remove(0);

		if (!this.elements.contains(removedElement))
		{
			this.unsortedElements.remove(removedElement);
		}

		return removedElement;
	}

	public T removeLast()
	{
		if (this.elements.isEmpty())
		{
			throw new IllegalStateException();
		}

		final T removedElement = this.elements.remove(this.elements.size() - 1);

		if (!this.elements.contains(removedElement))
		{
			this.unsortedElements.remove(removedElement);
		}

		return removedElement;
	}

	public T getFirst()
	{
		if (this.elements.isEmpty())
		{
			throw new IllegalStateException();
		}

		return this.elements.get(0);
	}

	public T getLast()
	{
		if (this.elements.isEmpty())
		{
			throw new IllegalStateException();
		}

		return this.elements.get(this.elements.size() - 1);
	}

	public Path<T> copy()
	{
		final Path<T> pathCopy = new Path<>();
		pathCopy.addElements(this.elements);
		if (this.isInGraph) pathCopy.markAsInGraph();

		return pathCopy;
	}

	public Path<T> truncateBetween(final T leftElem,
								   final T rightElem)
	{
		System.out.println("Left node: " + leftElem);
		System.out.println("Right node: " + rightElem);
		final Path<T> path = new Path<>();
		boolean started = false;

		for (T element : this.elements)
		{
			System.out.println("Current element: " + element);

			if (leftElem == null
				|| element.equals(leftElem))
			{
				System.out.println("started");
				started = true;
			}

			if (started)
			{
				path.add(element);
			}

			if (element.equals(rightElem))
			{
				break;
			}
		}

		return path;
	}

	public Path<T> truncateBetween(final T leftElem,
								   final Collection<T> rightElems)
	{
		for (T elem : rightElems)
		{
			if (this.contains(elem))
			{
				return this.truncateBetween(leftElem, elem);
			}
		}

		throw new IllegalStateException();
	}

	public Path<T> truncateBetween(final Collection<T> leftElems,
								   final T rightElem)
	{
		for (T elem : leftElems)
		{
			if (this.contains(elem))
			{
				return this.truncateBetween(elem, rightElem);
			}
		}

		throw new IllegalStateException();
	}

	public Path<T> truncateAfter(final T rightElem)
	{
		return this.truncateBetween((T) null, rightElem);
	}

	public Path<T> truncateBefore(final T leftElem)
	{
		return this.truncateBetween(leftElem, (T) null);
	}

	public int size()
	{
		return this.elements.size();
	}

	public T get(final int position)
	{
		if (position < 0
			|| position > this.elements.size() - 1)
		{
			throw new IndexOutOfBoundsException();
		}

		return this.elements.get(position);
	}

	public int indexOf(final T element)
	{
		return this.elements.indexOf(element);
	}

	public int lastIndexOf(final T element)
	{
		return this.elements.lastIndexOf(element);
	}

	public ArrayList<T> elements()
	{
		return this.elements;
	}

	public HashSet<T> unsortedElements()
	{
		return this.unsortedElements;
	}

	public boolean contains(final T element)
	{
		return this.unsortedElements.contains(element);
	}

	public boolean containsBefore(final T element,
								  final T bound)
	{
		for (T currentElement : this.elements())
		{
			if (currentElement.equals(element))
			{
				return true;
			}
			else if (currentElement.equals(bound))
			{
				return false;
			}
		}

		return false;
	}

	public boolean containsBeforeAParallelMerge(final Node element)
	{
		return this.containsBeforeAParallelMerge(element, new HashSet<>());
	}

	public boolean containsBeforeAParallelMerge(final Node element,
												final HashSet<Node> allowedMerges)
	{
		for (T currentElement : this.elements())
		{
			if (!(currentElement instanceof Node)) return false;

			if (currentElement.equals(element))
			{
				return true;
			}
			else if (!allowedMerges.contains(currentElement)
					&& ((Node) currentElement).bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
					&& ((Gateway) ((Node) currentElement).bpmnObject()).isMergeGateway())
			{
				return false;
			}
		}

		return false;
	}

	public HashSet<T> intersect(final Path<T> path)
	{
		return this.intersect(path.elements());
	}

	public HashSet<T> intersect(final Collection<T> elements)
	{
		final HashSet<T> intersection = new HashSet<>();

		for (T element : this.unsortedElements)
		{
			if (elements.contains(element))
			{
				intersection.add(element);
			}
		}

		return intersection;
	}

	public ArrayList<T> intersectAfter(final Path<T> path,
									 final T element)
	{
		return this.intersectAfter(path.elements(), element);
	}

	public ArrayList<T> intersectAfter(final List<T> elements,
									 final T element)
	{
		final ArrayList<T> intersection = new ArrayList<>();
		final int elementIndex = elements.indexOf(element);
		final int elementIndexInCurrentPath = this.elements.indexOf(element);

		if (elementIndex == -1
			|| elementIndexInCurrentPath == -1)
		{
			throw new IllegalStateException();
		}

		for (int i = elementIndex; i < elements.size(); i++)
		{
			final T currentElement = elements.get(i);

			for (int j = elementIndexInCurrentPath; j < this.elements.size(); j++)
			{
				final T currentLocalElement = this.elements.get(j);

				if (currentElement.equals(currentLocalElement))
				{
					intersection.add(currentElement);
				}
			}
		}

		return intersection;
	}

	/**
	 * This method verifies whether the given elements are all
	 * belonging to the current path and returns true in this case.
	 * If at least one given element is not in the path, it returns
	 * false
	 *
	 * @param elements the list of elements to verify
	 * @return true if all elements belong to the current path, false otherwise.
	 */
	public boolean contains(final Collection<T> elements)
	{
		for (T element : elements)
		{
			if (!this.unsortedElements.contains(element))
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * This method verifies whether the intersection of the given elements
	 * and the elements of the current path is non-empty, in which case it
	 * returns true.
	 *
	 * @param elements the list of elements to verify
	 * @return true if the intersection of sets is non-empty, false otherwise
	 */
	public boolean hasNonEmptyIntersectionWith(final Collection<T> elements)
	{
		for (T element : elements)
		{
			if (!this.unsortedElements.contains(element))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * This method verifies whether the intersection of the elements of the given path
	 * and the elements of the current path is non-empty, in which case it
	 * returns true.
	 *
	 * @param path the path to verify
	 * @return true if the intersection of sets is non-empty, false otherwise
	 */
	public boolean hasNonEmptyIntersectionWith(final Path<T> path)
	{
		return this.hasNonEmptyIntersectionWith(path.elements());
	}

	/**
	 * This method verifies whether the intersection of the given elements
	 * and the elements of the current path is non-empty, in which case it
	 * returns true.
	 *
	 * @param elements the list of elements to verify
	 * @return true if the intersection of sets is non-empty, false otherwise
	 */
	public boolean hasNonEmptyIntersectionWith(final List<T> elements,
											   final T loopElement)
	{
		final int loopNodeIndex = elements.indexOf(loopElement);

		for (int i = loopNodeIndex + 1; i < elements.size(); i++)
		{
			final T element = elements.get(i);

			if (!element.equals(loopElement))
			{
				if (this.unsortedElements.contains(element))
				{
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * This method verifies whether the intersection of the elements of the given path
	 * and the elements of the current path is non-empty, in which case it
	 * returns true.
	 *
	 * @param path the path to verify
	 * @return true if the intersection of sets is non-empty, false otherwise
	 */
	public boolean hasNonEmptyIntersectionWith(final Path<T> path,
											   final T loopElement)
	{
		return this.hasNonEmptyIntersectionWith(path.elements(), loopElement);
	}

	//Overrides
	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder("[");
		String separator = "";

		for (T element : this.elements)
		{
			builder.append(separator)
					.append(element.toString());
			separator = ", ";
		}

		builder.append("]");

		return builder.toString();
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Path<?>))
		{
			return false;
		}

		return this.elements.equals(((Path<?>) o).elements());
	}

	@Override
	public int hashCode()
	{
		return this.elements().hashCode();
	}
}
