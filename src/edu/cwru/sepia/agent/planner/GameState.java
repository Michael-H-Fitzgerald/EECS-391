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

public class GameState implements Comparable<GameState> {
	private static final String TOWNHALL_NAME = "townhall";
	private static final String GOLD_RESOURCE_NAME = "GOLD_MINE";
	private static final int MAX_RESOURCE_AMOUNT_TO_TAKE = 100;
	public static int PEASANT_TEMPLATE_ID;
	private static final int BUILD_PESANT_OFFSET = 20000000;
	private static final int REQUIRED_GOLD_TO_BUILD = 400;
	private static final int MAX_NUM_PEASANTS = 3;
	private static int requiredGold;
	private static int requiredWood;
	private static int townHallId;
	public static Position TOWN_HALL_POSITION;
	private static boolean buildPeasants = false;
	
	private int obtainedGold = 0;
	private int obtainedWood = 0;
	
	private int nextId = 0;
	private int buildPeasantOffset = 0;
	
	private List<Peasant> peasants = new ArrayList<Peasant>(3);
	private List<Resource> resources = new ArrayList<Resource>(7);
	
	private List<StripsAction> plan = new ArrayList<StripsAction>();

	/**
	 * 
	 * @param state The current stateview at the time the plan is being created
	 * @param playernum The player number of agent that is planning
	 * @param requiredGold The goal amount of gold (e.g. 200 for the small scenario)
	 * @param requiredWood The goal amount of wood (e.g. 200 for the small scenario)
	 * @param buildPeasants True if the BuildPeasant action should be considered
	 */
	public GameState(State.StateView state, int playernum, int requiredGold, int requiredWood, boolean buildPeasants) {
		GameState.requiredGold = requiredGold;
		GameState.requiredWood = requiredWood;
		GameState.buildPeasants = buildPeasants;
		state.getAllResourceNodes().stream().forEach(e -> {
			Position position = new Position(e.getXPosition(), e.getYPosition());
			if(e.getType().name().equals(GOLD_RESOURCE_NAME)){
				resources.add(new Gold(e.getID(), e.getAmountRemaining(), position));
			} else {
				resources.add(new Wood(e.getID(), e.getAmountRemaining(), position));
			}
		});
		state.getAllUnits().stream().forEach(e -> {
			Position position = new Position(e.getXPosition(), e.getYPosition());
			if(e.getTemplateView().getName().toLowerCase().equals(TOWNHALL_NAME)){
				GameState.TOWN_HALL_POSITION = position;
				GameState.townHallId = e.getID();
			} else {
				GameState.PEASANT_TEMPLATE_ID = e.getTemplateView().getID();
				this.peasants.add(new Peasant(e.getID(), position));
			}
		});
		this.nextId = 1 + this.peasants.size() + this.resources.size();
	}
	
	public GameState(GameState state){
		this.obtainedGold = state.obtainedGold;
		this.obtainedWood = state.obtainedWood;
		this.buildPeasantOffset = state.buildPeasantOffset;
		this.nextId = state.nextId;
		state.peasants.stream().forEach(e -> this.peasants.add(new Peasant(e)));
		state.resources.stream().forEach(e -> {
			if(e.isGold()){
				this.resources.add(new Gold(e));
			} else {
				this.resources.add(new Wood(e));
			}
		});	
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
		if(children.size() == 0){
			children.addAll(generateMoveActionChildren());
			children.addAll(generateHarvestActionChildren());
			children.addAll(generateDepositActionChildren());
		}
		return children;
	}

	private Collection<? extends GameState> generateMoveActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(int i = 0; i < this.peasants.size(); i++){
			Peasant peasant = this.peasants.get(i);
			if(!peasant.hasResource()){
				if(!peasantCanHarvest(peasant)){
					for(Resource resource : this.resources){
						if(resource.getAmountLeft() > MAX_RESOURCE_AMOUNT_TO_TAKE){
							if((resource.isGold() && obtainedGold < requiredGold) || (resource.isWood() && obtainedWood < requiredWood)){
								GameState child = new GameState(this);
								List<Position> adjacentList = resource.getPosition().getAdjacentPositions();
								MoveAction action = new MoveAction(peasant, adjacentList.get(i));
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
				MoveAction action = new MoveAction(peasant, TOWN_HALL_POSITION.getAdjacentPositions().get(i));
				if(action.preconditionsMet(child)){
					action.apply(child);
					children.add(child);
				}
			}
		}
		return children;
	}
	
	private boolean peasantCanHarvest(Peasant peasant) {
		return peasant.getPosition().getAdjacentPositions().stream().anyMatch(e -> this.isResourceLocation(e));		
	}

	private boolean isResourceLocation(Position destination) { 
		return this.resources.stream().anyMatch(e -> e.getPosition().equals(destination));
	}

	private Collection<? extends GameState> generateHarvestActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		for(Peasant peasant : this.peasants){
			if(!peasant.hasResource()){
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
			if(peasant.hasResource() && peasant.getPosition().isAdjacent(TOWN_HALL_POSITION)){
				GameState child = new GameState(this);
				DepositAction action = new DepositAction(peasant);
				action.apply(child);
				children.add(child);
			}
		}
		return children;
	}
	
	private Collection<? extends GameState> generateBuildActionChildren() {
		List<GameState> children = new ArrayList<GameState>();
		if(buildPeasants && this.canBuild()){
			GameState child = new GameState(this);
			BuildAction action = new BuildAction(townHallId, PEASANT_TEMPLATE_ID);
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
			if(peasant.hasResource()){
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

	public Position getPeasantPosition(int peasantId) {
		return getPeasantWithId(peasantId).getPosition();
	}

	public boolean isOccupied(Position destination) {
		return 	this.peasants.stream().anyMatch(e -> e.getPosition().equals(destination)) || 
				isResourceLocation(destination); 
	}

	public void movePosition(StripsAction action, int peasantId, Position destination) {
		getPeasantWithId(peasantId).setPosition(destination);
		plan.add(action);
	}

	public boolean playerIsHolding(int peasantId) {
		Peasant peasant = getPeasantWithId(peasantId);
		return peasant.hasGold() || peasant.hasWood();
	}

	public void deposit(StripsAction action, int peasantId) {
		Peasant peasant = getPeasantWithId(peasantId);
		if(peasant.hasGold()){
			this.obtainedGold = this.obtainedGold + peasant.getNumGold();
			peasant.setNumGold(0);
		} else {
			this.obtainedWood = this.obtainedWood + peasant.getNumWood();
			peasant.setNumWood(0);
		}
		plan.add(action);
	}

	public Position getResourcePosition(int resourceId) {
		return getResourceWithId(resourceId).getPosition();
	}

	public boolean hasResources(int resourceId) {
		return getResourceWithId(resourceId).hasRemaining();
	}

	public void harvest(StripsAction action, int peasantId, int resourceId) {
		Resource resource = getResourceWithId(resourceId);
		Peasant peasant = getPeasantWithId(peasantId);
		if(resource.isGold()){
			peasant.setNumGold(Math.min(100, resource.getAmountLeft()));
			resource.setAmountLeft(Math.max(0, resource.getAmountLeft() - 100));
		} else {
			peasant.setNumWood(Math.min(100, resource.getAmountLeft()));
			resource.setAmountLeft(Math.max(0, resource.getAmountLeft() - 100));
		}
		plan.add(action);
	}

	public boolean canBuild() {
		return obtainedGold >= REQUIRED_GOLD_TO_BUILD && this.peasants.size() < MAX_NUM_PEASANTS;
	}

	public void build(StripsAction action) {
		this.obtainedGold = this.obtainedGold - REQUIRED_GOLD_TO_BUILD;
		Peasant peasant = null;
		if(this.peasants.size() == 1){
			peasant = new Peasant(nextId, new Position(TOWN_HALL_POSITION.x - 1, TOWN_HALL_POSITION.y));
			nextId++;
		} else {
			peasant = new Peasant(nextId, new Position(TOWN_HALL_POSITION.x - 1, TOWN_HALL_POSITION.y - 1));
		}		
		this.peasants.add(peasant);
		this.buildPeasantOffset = this.buildPeasantOffset + BUILD_PESANT_OFFSET;
		plan.add(action);
	}

	public Peasant getPeasantWithId(int peasantId){
		return this.peasants.stream().filter(e -> e.getId() == peasantId).findFirst().get();
	}
	
	public Resource getResourceWithId(int resourceId){
		return this.resources.stream().filter(e -> e.getId() == resourceId).findFirst().get();
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + buildPeasantOffset;
		result = prime * result + obtainedGold;
		result = prime * result + obtainedWood;
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
		if (buildPeasantOffset != other.buildPeasantOffset)
			return false;
		if (obtainedGold != other.obtainedGold)
			return false;
		if (obtainedWood != other.obtainedWood)
			return false;
		if (peasants == null) {
			if (other.peasants != null)
				return false;
		} else if (!peasants.equals(other.peasants))
			return false;
		return true;
	}
}
