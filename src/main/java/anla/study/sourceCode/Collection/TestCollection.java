package anla.study.sourceCode.Collection;

import java.util.HashMap;

public class TestCollection {

	public static void main(String[] args) {
		HashMap<Key, Key> map = new HashMap<Key, Key>();
		for (int i = 0; i < 15; i++) {
			map.put(new Key(i), new Key(i));
			System.out.println(i);
		}

		java.util.TreeMap<Key, Key> tree = new java.util.TreeMap<Key, Key>();

		for (int i = 0; i < 15; i++) {
			tree.put(new Key(5), new Key(i));
		}

		System.out.println(map.containsValue(new String("3")));
	}
}

class Key implements Comparable<Key> {
	int key;

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Key other = (Key) obj;
		if (key == other.key)
			return true;
		else
			return true;
	}

	public Key(int key) {
		this.key = key;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + key;
		return result;
	}

	public int compareTo(Key o) {
		if (this.key > o.key) {
			return 1;
		} else {
			return 0;
		}
	}
}

