package edu.cwru.sepia.agent.planner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import edu.cwru.sepia.agent.planner.actions.BuildAction;
import edu.cwru.sepia.agent.planner.actions.DepositAction;
import edu.cwru.sepia.agent.planner.actions.HarvestAction;
import edu.cwru.sepia.agent.planner.actions.MoveAction;
import edu.cwru.sepia.agent.planner.actions.StripsAction;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

/**
 * This class is used to represent the state of the game after applying one of the avaiable actions. It will also
 * track the A* specific information such as the parent pointer and the cost and heuristic function. Remember that
 * unlike the path planning A* from the first assignment the cost of an action may be more than 1. Specifically the cost
 * of executing a compound action such as move can be more than 1. You will need to account for this in your heuristic
 * and your cost function.
 *
 * The first instance is constructed from the StateView object (like in PA2). Implement the methods provided and
 * add any other methods and member variables you need.
 *
 * Some useful API calls for the state view are
 *
 * state.getXExtent() and state.getYExtent() to get the map size
 *
 * I recommend storing the actions that generated the instance of the GameState in this class using whatever
 * class/structure you use to represent actions.
 */
public class GameState implements Comparable<GameState> {
	private int playernum;
	private int requiredGold;
	private int requiredWood;
	private boolean buildPeasants;

	private int obtainedGold = 0;
	private int obtainedWood = 0;
	
	private List<StripsAction> plan = new ArrayList<StripsAction>();
	private int townHallId;
	private UnitView townHall;
	private Position townHallPosition;
	private int peasantTemplateId;
	private List<Peasant> peasants = new ArrayList<Peasant>(3);
	private List<Resource> resources = new ArrayList<Resource>(7);
	private int buildPeasantOffset = 0;
	
	public class Peasant{
		public int id;
		public Position position;
		int numGold = 0;
		int numWood = 0;
		
		public Peasant(int id, Position position){
			this.id = id;
			this.position = position;
		}
		public Peasant(Peasant value) {
			this.id = value.id;
			this.position = new Position(value.position);
			this.numGold = value.numGold;
			this.numWood = value.numWood;
		}
		public boolean hasGold(){
			return numGold > 0;
		}
		public boolean hasWood(){
			return numWood > 0;
		}
		public boolean isCarrying(){
			return hasGold() || hasWood();
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + id;
			result = prime * result + numGold;
			result = prime * result + numWood;
			result = prime * result + ((position == null) ? 0 : position.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Peasant other = (Peasant) obj;
			if (id != other.id)
				return false;
			if (numGold != other.numGold)
				return false;
			if (numWood != other.numWood)
				return false;
			if (position == null) {
				if (other.position != null)
					return false;
			} else if (!position.equals(other.position))
				return false;
			return true;
		}		
		
	}
	
	public abstract class Resource {
		public int id;
		int amountLeft;
		public Position position;
		public abstract boolean isGold();
		public abstract boolean isWood();
	}
	
	public class Gold extends Resource {
		public Gold(int id, int amountLeft, Position position){
			this.id = id;
			this.amountLeft = amountLeft;
			this.position = position;
		}

		public Gold(Resource value) {
			this.id = value.id;
			this.amountLeft = value.amountLeft;
			this.position = new Position(value.position);
		}

		@Override
		public boolean isGold() {
			return true;
		}

		@Override
		public boolean isWood() {
			return false;
		}
	}
	
	public class Wood extends Resource {
		public Wood(int id, int amountLeft, Position position){
			this.id = id;
			this.amountLeft = amountLeft;
			this.position = position;
		}

		public Wood(Resource value) {
			this.id = value.id;
			this.amountLeft = value.amountLeft;
			this.position = new Position(value.position);
		}

		@Override
		public boolean isGold() {
			return false;
		}

		@Override
		public boolean isWood() {
			return true;
		}
	}
	
	public Stack<StripsAction> getPlan(){
		Stack<StripsAction> plan = new Stack<StripsAction>();
		for(int i = this.plan.size() - 1; i > -1; i--){
			plan.push(this.plan.get(i));
		}
		return plan;
	}

	/**
	 * 
	 * @param state The current stateview at the time the plan is being created
	 * @param playernum The player number of agent that is planning
	 * @param requiredGold The goal amount of gold (e.g. 200 for the small scenario)
	 * @param requiredWood The goal amount of wood (e.g. 200 for the small scenario)
	 * @param buildPeasants True if the BuildPeasant action should be considered
	 */
	public GameState(State.StateView state, int playernum, int requiredGold, int requiredWood, boolean buildPeasants) {
		this.playernum = playernum;
		this.requiredGold = requiredGold;
		this.requiredWood = requiredWood;
		this.buildPeasants = buildPeasants;
		state.getAllResourceNodes().stream().forEach(e -> {
			Position position = new Position(e.getXPosition(), e.getYPosition());
			if(e.getType().name().equals("GOLD_MINE")){
				resources.add(new Gold(e.getID(), e.getAmountRemaining(), position));
			} else {
				resources.add(new Wood(e.getID(), e.getAmountRemaining(), position));
			}
		});
		state.getAllUnits().stream().forEach(e -> {
			Position position = new Position(e.getXPosition(), e.getYPosition());
			if(e.getTemplateView().getName().toLowerCase().equals("townhall")){
				this.townHall = e;
				this.townHallPosition = position;
				this.townHallId = e.getID();
			} else {
				this.peasantTemplateId = e.getTemplateView().getID();
				this.peasants.add(new Peasant(e.getID(), position));
			}
		});
	}
	
	public GameState(GameState state){
		this.playernum = state.playernum;
		this.obtainedGold = state.obtainedGold;
		this.obtainedWood = state.obtainedWood;
		this.requiredGold = state.requiredGold;
		this.requiredWood = state.requiredWood;
		this.buildPeasants = state.buildPeasants;
		this.buildPeasantOffset = state.buildPeasantOffset;
		state.peasants.stream().forEach(e -> this.peasants.add(new Peasant(e)));
		state.resources.stream().forEach(e -> {
			if(e.isGold()){
				this.resources.add(new Gold(e));
			} else {
				this.resources.add(new Wood(e));
			}
		});
		
		this.townHallId = state.townHallId;
		this.townHall = state.townHall;
		this.townHallPosition = state.townHallPosition;
		this.peasantTemplateId = state.peasantTemplateId;		
		state.plan.stream().forEach(e -> plan.add(e));
	}

	/**
	 *
	 * @return true if the goal conditions are met in this instance of game state.
	 */
	public boolean isGoal() {
		return obtainedGold >= requiredGold && obtainedWood >= requiredWood;
	}

	/**
	 * The branching factor of this search graph are much higher than the planning. Generate all of the possible
	 * successor states and their associated actions in this method.
	 *
	 * @return A list of the possible successor states and their associated actions
	 */
	public List<GameState> generateChildren() {
		List<GameState> children = new ArrayList<GameState>();
		children.addAll(generateBuildActionChildren());
		children.addAll(generateMoveActionChildren());
		children.addAll(generateHarvestActionChildren());
		children.addAll(generateDepositActionChildren());
		return children;
	}

	private Collection<? extends GameState> generateMoveActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(Peasant peasant : this.peasants){
			if(!peasant.isCarrying()){
				if(!peasantCanHarvest(peasant)){
					for(Resource resource : this.resources){
						if(resource.amountLeft > 100){
							if((resource.isGold() && obtainedGold < requiredGold) || (resource.isWood() && obtainedWood < requiredWood)){
								GameState child = new GameState(this);
								MoveAction action = new MoveAction(peasant.id, resource.position.getAdjacentPositions().get(0));
								if(action.preconditionsMet(child)){
									action.apply(child);
									children.add(child);
								}
							}
						}
					}
				}
			} else {
				GameState child = new GameState(this);
				MoveAction action = new MoveAction(peasant.id, townHallPosition.getAdjacentPositions().get(0));
				if(action.preconditionsMet(child)){
					action.apply(child);
					children.add(child);
				}
			}
		}
		return children;
	}
	
	private boolean peasantCanHarvest(Peasant peasant) {
		return peasant.position.getAdjacentPositions().stream().anyMatch(e -> this.isResourceLocation(e));		
	}

	private boolean isResourceLocation(Position destination) { 
		return this.resources.stream().anyMatch(e -> e.position.equals(destination));
	}

	private Collection<? extends GameState> generateHarvestActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(Peasant peasant : this.peasants){
			if(!peasant.isCarrying()){
				for(Resource resource : this.resources){
					GameState child = new GameState(this);
					HarvestAction action = new HarvestAction(peasant, resource);
					if(action.preconditionsMet(child)){
						action.apply(child);
						children.add(child);
					}
				}
			}
		}
		return children;
	}
	
	private Collection<? extends GameState> generateDepositActionChildren(){
		List<GameState> children = new ArrayList<GameState>();
		for(Peasant peasant : this.peasants){
			if(peasant.position.isAdjacent(townHallPosition) && peasant.isCarrying()){
				GameState child = new GameState(this);
				DepositAction action = new DepositAction(peasant.id, this);
				if(action.preconditionsMet(child)){
					action.apply(child);
					children.add(child);
				}
			}
		}
		return children;
	}
	
	private Collection<? extends GameState> generateBuildActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		if(buildPeasants && this.canBuild()){
			GameState child = new GameState(this);
			BuildAction action = new BuildAction(townHallId, peasantTemplateId);
			if(action.preconditionsMet(child)){
				action.apply(child);
				children.add(child);
			}
		}
		return children;
	}

	/**
	 * Write your heuristic function here. Remember this must be admissible for the properties of A* to hold. If you
	 * can come up with an easy way of computing a consistent heuristic that is even better, but not strictly necessary.
	 *
	 * Add a description here in your submission explaining your heuristic.
	 *
	 * @return The value estimated remaining cost to reach a goal state from this state.
	 */
	public double heuristic() {
		double result = obtainedGold + obtainedWood + buildPeasantOffset;
		for(Peasant peasant : this.peasants){
			if(peasant.isCarrying()){
				result++;
			}
		}
		return result;
	}

	/**
	 *
	 * Write the function that computes the current cost to get to this node. This is combined with your heuristic to
	 * determine which actions/states are better to explore.
	 *
	 * @return The current cost to reach this goal
	 */
	public double getCost() {
		return plan.size();
	}

	/**
	 * This is necessary to use your state in the Java priority queue. See the official priority queue and Comparable
	 * interface documentation to learn how this function should work.
	 *
	 * @param o The other game state to compare
	 * @return 1 if this state costs more than the other, 0 if equal, -1 otherwise
	 */
	@Override
	public int compareTo(GameState o) {
		if(this.heuristic() + this.getCost() > o.heuristic() + o.getCost()){
			return 1;
		} else if(this.heuristic() + this.getCost() < o.heuristic() + o.getCost()){
			return -1;
		}
		return 0;
	}

	public Position getPeasantPosition(int peasantId) {
		return getPeasantWithId(peasantId).position;
	}

	public boolean isOccupied(Position destination) {
		return 	this.peasants.stream().anyMatch(e -> e.position.equals(destination)) || 
				isResourceLocation(destination); 
	}

	public void movePosition(StripsAction action, int peasantId, Position destination) {
		getPeasantWithId(peasantId).position = destination;
		plan.add(action);
	}

	public Position getTownHallPosition() {
		return townHallPosition;
	}

	public boolean playerIsHolding(int peasantId) {
		Peasant peasant = getPeasantWithId(peasantId);
		return peasant.hasGold() || peasant.hasWood();
	}

	public void deposit(StripsAction action, int peasantId) {
		Peasant peasant = getPeasantWithId(peasantId);
		if(peasant.hasGold()){
			this.obtainedGold = this.obtainedGold + peasant.numGold;
			peasant.numGold = 0;
		} else {
			this.obtainedWood = this.obtainedWood + peasant.numWood;
			peasant.numWood = 0;
		}
		plan.add(action);
	}

	public Position getResourcePosition(int resourceId) {
		return getResourceWithId(resourceId).position;
	}

	public boolean hasResources(int resourceId) {
		return getResourceWithId(resourceId).amountLeft > 0;
	}

	public void harvest(StripsAction action, int peasantId, int resourceId) {
		Resource resource = getResourceWithId(resourceId);
		Peasant peasant = getPeasantWithId(peasantId);
		if(resource.isGold()){
			peasant.numGold = Math.min(100, resource.amountLeft);
			resource.amountLeft = Math.max(0, resource.amountLeft - 100);
		} else {
			peasant.numWood = Math.min(100, resource.amountLeft);
			resource.amountLeft = Math.max(0, resource.amountLeft - 100);
		}
		plan.add(action);
	}

	public boolean canBuild() {
		return obtainedGold > 400 && this.peasants.size() < 3;
	}

	public void build(StripsAction action) {
		this.obtainedGold = this.obtainedGold - 400;
		int id = 2;
		Peasant peasant = new Peasant(id, new Position(townHallPosition.x + 1, townHallPosition.y));
		this.peasants.add(peasant);
		this.buildPeasantOffset = this.buildPeasantOffset + 500;
		plan.add(action);
	}

	public Peasant getPeasantWithId(int peasantId){
		return this.peasants.stream().filter(e -> e.id == peasantId).findFirst().get();
	}
	
	public Resource getResourceWithId(int resourceId){
		return this.resources.stream().filter(e -> e.id == resourceId).findFirst().get();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + obtainedGold;
		result = prime * result + obtainedWood;
		result = prime * result + buildPeasantOffset;
		result = prime * result + ((peasants == null) ? 0 : peasants.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GameState other = (GameState) obj;
		if (obtainedGold != other.obtainedGold)
			return false;
		if (obtainedWood != other.obtainedWood)
			return false;
		if (buildPeasantOffset != other.buildPeasantOffset)
			return false;
		if (peasants == null) {
			if (other.peasants != null)
				return false;
		} else if (!peasants.equals(other.peasants))
			return false;
		return true;
	}

	
	
}
