package edu.cwru.sepia.agent.planner;

public abstract class Resource {
	public int id;
	int amountLeft;
	public Position position;
	public abstract boolean isGold();
	public abstract boolean isWood();
	public boolean hasRemaining() {
		return amountLeft > 0;
	}
}