package other;

public class Pair<T,U>
{
	private static final int FIRST_ELEMENT_HASH_CONSTANT = 7;
	private static final int SECOND_ELEMENT_HASH_CONSTANT = 15;
	private T first;
	private U second;

	public Pair(T first,
				U second)
	{
		this.first = first;
		this.second = second;
	}

	public T first()
	{
		return this.first;
	}

	public U second()
	{
		return this.second;
	}

	public void setFirst(T first)
	{
		this.first = first;
	}

	public void setSecond(U second)
	{
		this.second = second;
	}

	public Pair<T,U> copy()
	{
		return new Pair<>(this.first, this.second);
	}

	@Override
	public boolean equals(final Object o)
	{
		if (!(o instanceof Pair<?,?>))
		{
			return false;
		}

		return ((Pair<?, ?>) o).first().equals(this.first)
				&& ((Pair<?, ?>) o).second().equals(this.second);
	}

	@Override
	public int hashCode()
	{
		final int hashCodeFirst = 31 + (this.first == null ? 0 : this.first.hashCode());
		return 31 * hashCodeFirst + (this.second == null ? 0 : this.second.hashCode());
	}

	@Override
	public String toString()
	{
		return "(" + this.first.toString() + ", " + this.second.toString() + ")";
	}
}