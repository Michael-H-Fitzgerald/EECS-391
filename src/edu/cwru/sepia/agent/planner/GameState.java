package edu.cwru.sepia.agent.planner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	
	private Map<Integer, Position> peasantLocations = new HashMap<Integer, Position>(3);
	private Map<Integer, Position> resourceLocations = new HashMap<Integer, Position>(7);
	private List<StripsAction> plan = new ArrayList<StripsAction>();
	private int townHallId;
	private UnitView townHall;
	private Position townHallPosition;
	private int peasantTemplateId;
	private Map<Integer, Peasant> peasants = new HashMap<Integer, Peasant>(3);
	private Map<Integer, Resource> resources = new HashMap<Integer, Resource>(7);
	
	public class Peasant{
		int id;
		Position position;
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
	}
	
	public abstract class Resource {
		int id;
		int amountLeft;
		Position position;
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
			resourceLocations.put(e.getID(), position);
			if(e.getType().equals("gold")){
				resources.put(e.getID(), new Gold(e.getID(), e.getAmountRemaining(), position));
			} else {
				resources.put(e.getID(), new Wood(e.getID(), e.getAmountRemaining(), position));
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
				this.peasants.put(e.getID(), new Peasant(e.getID(), position));
				this.peasantLocations.put(e.getID(), position);
			}
		});
	}
	
	public GameState(GameState state){
		this.playernum = state.playernum;
		this.obtainedGold = state.obtainedGold;
		this.obtainedWood = state.obtainedWood;
		this.requiredGold = state.requiredGold;
		this.requiredWood = state.requiredWood;
		
		state.peasantLocations.entrySet().stream().forEach(e -> this.peasantLocations.put(e.getKey(), e.getValue()));
		state.resourceLocations.entrySet().stream().forEach(e -> this.resourceLocations.put(e.getKey(), e.getValue()));
		state.peasants.entrySet().stream().forEach(e -> this.peasants.put(e.getKey(), new Peasant(e.getValue())));
		state.resources.entrySet().stream().forEach(e -> {
			if(e.getValue().isGold()){
				this.resources.put(e.getKey(), new Gold(e.getValue()));
			} else {
				this.resources.put(e.getKey(), new Wood(e.getValue()));
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
		children.addAll(generateMoveActionChildren());
		children.addAll(generateHarvestActionChildren());
		children.addAll(generateDepositActionChildren());
		children.addAll(generateBuildActionChildren());
		return children;
	}

	private Collection<? extends GameState> generateMoveActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(Resource resource : this.resources.values()){
			if(resource.amountLeft > 100){
				for(Peasant peasant : this.peasants.values()){
					GameState child = new GameState(this);
					MoveAction action = new MoveAction(peasant.id, resource.position.getAdjacentPositions().get(0));
					if(action.preconditionsMet(child)){
						action.apply(child);
						children.add(child);
					}
				}
			}
		}
		return children;
	}
	
	private Collection<? extends GameState> generateHarvestActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(Resource resource : this.resources.values()){
			if(resource.amountLeft > 100){
				for(Peasant peasant : this.peasants.values()){
					GameState child = new GameState(this);
					HarvestAction action = new HarvestAction(peasant.id, resource.id, this);
					if(action.preconditionsMet(child)){
						action.apply(child);
						children.add(child);
					}
				}
			}
		}
		return children;
	}
	
	private Collection<? extends GameState> generateDepositActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(Peasant peasant : this.peasants.values()){
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
		return obtainedGold + obtainedWood;
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
		if(this.heuristic() > o.heuristic()){
			return 1;
		} else if(this.heuristic() < o.heuristic()){
			return -1;
		}
		return 0;
	}

	public Position getPeasantPosition(int peasantId) {
		return peasantLocations.get(peasantId);
	}
	
	/**
	 * This is necessary to use the GameState as a key in a HashSet or HashMap. Remember that if two objects are
	 * equal they should hash to the same value.
	 *
	 * @return An integer hashcode that is equal for equal states.
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (buildPeasants ? 1231 : 1237);
		result = prime * result + obtainedGold;
		result = prime * result + obtainedWood;
		result = prime * result + ((peasantLocations == null) ? 0 : peasantLocations.hashCode());
		result = prime * result + peasantTemplateId;
		result = prime * result + ((peasants == null) ? 0 : peasants.hashCode());
		result = prime * result + playernum;
		result = prime * result + requiredGold;
		result = prime * result + requiredWood;
		result = prime * result + ((resourceLocations == null) ? 0 : resourceLocations.hashCode());
		result = prime * result + ((resources == null) ? 0 : resources.hashCode());
		result = prime * result + ((townHall == null) ? 0 : townHall.hashCode());
		result = prime * result + townHallId;
		result = prime * result + ((townHallPosition == null) ? 0 : townHallPosition.hashCode());
		return result;
	}

	/**
	 * This will be necessary to use the GameState as a key in a Set or Map.
	 *
	 * @param o The game state to compare
	 * @return True if this state equals the other state, false otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GameState other = (GameState) obj;
		if (buildPeasants != other.buildPeasants)
			return false;
		if (obtainedGold != other.obtainedGold)
			return false;
		if (obtainedWood != other.obtainedWood)
			return false;
		if (peasantLocations == null) {
			if (other.peasantLocations != null)
				return false;
		} else if (!peasantLocations.equals(other.peasantLocations))
			return false;
		if (peasantTemplateId != other.peasantTemplateId)
			return false;
		if (peasants == null) {
			if (other.peasants != null)
				return false;
		} else if (!peasants.equals(other.peasants))
			return false;
		if (playernum != other.playernum)
			return false;
		if (requiredGold != other.requiredGold)
			return false;
		if (requiredWood != other.requiredWood)
			return false;
		if (resourceLocations == null) {
			if (other.resourceLocations != null)
				return false;
		} else if (!resourceLocations.equals(other.resourceLocations))
			return false;
		if (resources == null) {
			if (other.resources != null)
				return false;
		} else if (!resources.equals(other.resources))
			return false;
		if (townHall == null) {
			if (other.townHall != null)
				return false;
		} else if (!townHall.equals(other.townHall))
			return false;
		if (townHallId != other.townHallId)
			return false;
		if (townHallPosition == null) {
			if (other.townHallPosition != null)
				return false;
		} else if (!townHallPosition.equals(other.townHallPosition))
			return false;
		return true;
	}

	public boolean isOccupied(Position destination) {
		return resourceLocations.containsValue(destination) || peasantLocations.containsValue(destination);
	}

	public void movePosition(StripsAction action, int peasantId, Position destination) {
		this.peasantLocations.remove(peasantId);
		this.peasantLocations.put(peasantId, destination);
		this.peasants.get(peasantId).position = destination;
		plan.add(action);
	}

	public Position getTownHallPosition() {
		return townHallPosition;
	}

	public boolean playerIsHolding(int peasantId) {
		Peasant peasant = this.peasants.get(peasantId);
		return peasant.hasGold() || peasant.hasWood();
	}

	public void deposit(StripsAction action, int peasantId) {
		Peasant peasant = this.peasants.get(peasantId);
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
		return this.resourceLocations.get(resourceId);
	}

	public boolean hasResources(int resourceId) {
		return this.resources.get(resourceId).amountLeft > 0;
	}

	public void harvest(StripsAction action, int peasantId, int resourceId) {
		Resource resource = this.resources.get(resourceId);
		Peasant peasant = this.peasants.get(peasantId);
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
		this.peasants.put(id, peasant);
		plan.add(action);
	}

}
