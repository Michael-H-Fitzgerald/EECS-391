package edu.cwru.sepia.agent.minimax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.util.Direction;

public class GameState {
	public static final double MAX_UTILITY = Double.POSITIVE_INFINITY;
	public static final double MIN_UTILITY = Double.NEGATIVE_INFINITY;
	public static final String ACTION_MOVE_NAME = Action.createPrimitiveMove(0, null).getType().name();
	public static final String ACTION_ATTACK_NAME = Action.createPrimitiveAttack(0, 0).getType().name();
	
	private Board board;
	private boolean ourTurn;
	private boolean utilityCalculated = false;
	private double utility = 0.0;

	/**
	 * Class containing agents and resources (with locations) and several helper methods
	 */
	private class Board {
		private Square[][] board;
		private Map<Integer, Agent> agents = new HashMap<Integer, Agent>();
		private Map<Integer, Resource> resources = new HashMap<Integer, Resource>();
		private int width;
		private int height;

		public Board(int x, int y){
			board = new Square[x][y];
			this.width = x;
			this.height = y;
		}

		public void addResource(int id, int x, int y){
			Resource resource = new Resource(id, x, y);
			board[x][y] = resource;
			resources.put(resource.getId(), resource);
		}

		public void addAgent(int id, int x, int y, int hp, int possibleHp, int attackDamage, int attackRange){
			Agent agent = new Agent(id, x, y, hp, possibleHp, attackDamage, attackRange);
			board[x][y] = agent;
			agents.put(id, agent);
		}

		private void moveAgentBy(int id, int xOffset, int yOffset){
			Agent agent = getAgent(id);
			int currentX = agent.getX();
			int currentY = agent.getY();
			int nextX = currentX + xOffset;
			int nextY = currentY + yOffset;
			board[currentX][currentY] = null;
			agent.setX(nextX);
			agent.setY(nextY);
			board[nextX][nextY] = agent;
		}
		
		public void attackAgent(Agent attacker, Agent attacked){
			if(attacked != null && attacker != null){
				attacked.hp = attacked.hp - attacker.attackDamage;
			}
		}

		public boolean isEmpty(int x, int y){
			return board[x][y] == null;
		}

		public boolean isResource(int x, int y){
			return board[x][y] != null && resources.containsKey(board[x][y].id);
		}
		
		public boolean isOnBoard(int x, int y){
			return x >= 0 && x < width && y >= 0 && y < height; 
		}

		public Agent getAgent(int id) {
			Agent agent = agents.get(id);
			if(!agent.isAlive()){
				return null;
			}
			return agent;
		}
		
		public Collection<Agent> getAliveGoodAgents(){
			return agents.values().stream().filter(e -> e.isGood() && e.isAlive()).collect(Collectors.toList());
		}
		
		public Collection<Agent> getAliveBadAgents(){
			return agents.values().stream().filter(e -> !e.isGood() && e.isAlive()).collect(Collectors.toList());
		}

		public double distance(Agent agent1, Agent agent2) {
			return (Math.abs(agent1.getX() - agent2.getX()) + Math.abs(agent1.getY() - agent2.getY())) - 1;
		}

		public double attackDistance(Agent agent1, Agent agent2){
			return Math.floor(Math.hypot(Math.abs(agent1.getX() - agent2.getX()), Math.abs(agent1.getY() - agent2.getY())));
		}
		
		private List<Integer> findAttackableAgents(Agent agent) {
			List<Integer> attackable = new ArrayList<Integer>();
			for(Agent otherAgent : agents.values()){
				if(otherAgent.getId() != agent.getId() && (otherAgent.isGood() != agent.isGood()) && 
						attackDistance(agent, otherAgent) <= agent.attackRange){
					attackable.add(otherAgent.getId());
				}
			}
			return attackable;
		}
	}

	/**
	 * Represents a single location or square on the playing board
	 */
	private abstract class Square {
		private int id;
		private int x;
		private int y;

		public Square(int id, int x, int y){
			this.id = id;
			this.x = x;
			this.y = y;
		}
		public int getId(){
			return this.id;
		}
		public int getX(){
			return this.x;
		}
		public void setX(int x){
			this.x = x;
		}
		public int getY(){
			return this.y;
		}
		public void setY(int y){
			this.y = y;
		}
	}

	/** 
	 * A representation of an agent either good (footman) or bad (archer)
	 *
	 */
	private class Agent extends Square {
		public int hp;
		public int possibleHp;
		public int attackDamage;
		public int attackRange;
		
		public Agent(int id, int x, int y, int hp, int possibleHp, int attackDamage, int attackRange) {
			super(id, x, y);
			this.hp = hp;
			this.possibleHp = possibleHp;
			this.attackDamage = attackDamage;
			this.attackRange = attackRange;
		}

		public boolean isGood(){
			return this.getId() == 0 || this.getId() == 1;
		}

		public boolean isAlive() {
			return hp > 0;
		}
	}

	/**
	 * A representation of non-agents on the board - trees
	 *
	 */
	private class Resource extends Square {
		public Resource(int id, int x, int y) {
			super(id, x, y);
		}		
	}

	/**
	 * Constructor that takes from a SEPIA state view to generate my representation of state
	 * 
	 * This is only called on the initial state all children state are generated by the other constructor.
	 * 
	 * @param state
	 */
	public GameState(State.StateView state) {
		this.board = new Board(state.getXExtent(), state.getYExtent());
		state.getAllUnits().stream().forEach( (e) -> {
			this.board.addAgent(e.getID(), e.getXPosition(), e.getYPosition(), e.getHP(), e.getHP(), e.getTemplateView().getBasicAttack(), e.getTemplateView().getRange());
		});

		state.getAllResourceNodes().stream().forEach( (e) -> {
			this.board.addResource(e.getID(), e.getXPosition(), e.getYPosition());
		});
		
		this.ourTurn = true;
	}   

	/**
	 * This constructor uses the non-SEPIA representation of the game and is called for all
	 * except the first creation of a GameState
	 * 
	 * @param gameState
	 */
	public GameState(GameState gameState) {
		this.board = new Board(gameState.board.width, gameState.board.height);
		gameState.board.agents.values().stream().forEach( (e) -> {
			this.board.addAgent(e.getId(), e.getX(), e.getY(), e.hp, e.possibleHp, e.attackDamage, e.attackRange);			
		});

		gameState.board.resources.values().stream().forEach( (e) -> {		
			this.board.addResource(e.getId(), e.getX(), e.getY());		
		});
		this.ourTurn = !gameState.ourTurn;
		this.utilityCalculated = gameState.utilityCalculated;
		this.utility = gameState.utility;
	}

	/**
	 * Determines the "goodness" of a state. Includes things like being able to attack an opponent
	 * current health and location relative to obstacles (resources) and enemies
	 * 
	 * For more information on each specific feature see comments on each ...Utility() function
	 * 
	 * @return
	 */
	public double getUtility() {
		if(this.utilityCalculated){
			return this.utility;
		}
		
		// Calculate features included
		this.utility += getHasGoodAgentsUtility();
		this.utility += getHasBadAgentsUtility();
		this.utility += getHealthUtility();
		this.utility += getDamageToEnemyUtility();
		this.utility += getCanAttackUtility();
		this.utility += getLocationUtility();
				
		this.utilityCalculated = true;
		return this.utility;
	}
	
	/**
	 * @return the number of good agents or the MIN_UTILITY if all good agents are dead (the game is over and we lost) 
	 */
	private double getHasGoodAgentsUtility() {
		return this.board.getAliveGoodAgents().isEmpty() ? MIN_UTILITY : this.board.getAliveGoodAgents().size();
	}
	
	/**
	 * @return the number of bad agents or the MAX_UTILITY if all bad agents are dead (the game is over and we won) 
	 */
	private double getHasBadAgentsUtility() {
		return this.board.getAliveBadAgents().isEmpty() ? MAX_UTILITY : this.board.getAliveBadAgents().size();
	}
	
	/**
	 * @return the amount of health each footman has
	 */
	private double getHealthUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.getAliveGoodAgents()){
			utility += agent.hp/agent.possibleHp;
		}
		return utility;
	}
	
	/**
	 * @return how much damage has been done to each archer
	 */
	private double getDamageToEnemyUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.getAliveBadAgents()){
			utility += agent.possibleHp - agent.hp;		
		}
		return utility;
	}
	
	/**
	 * @return the number of agents that are within range of the footmen
	 */
	private double getCanAttackUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.getAliveGoodAgents()){
			utility += this.board.findAttackableAgents(agent).size();		
		}
		return utility;
	}
	
	/**
	 * @return how optimal the footman positions are attempts to deal with obstacles (resources)
	 */
	private double getLocationUtility() {
		if(this.board.resources.isEmpty() ||
			noResourcesAreInTheArea()){
			return distanceFromEnemy() * -1;
		}
		return 0.0;
	}
	
	private boolean noResourcesAreInTheArea(){
		for(Agent goodGuy : this.board.getAliveGoodAgents()){
			for(Agent badGuy : this.board.getAliveBadAgents()){
				if(numResourceInAreaBetween(goodGuy, badGuy) != 0){
					return false;
				}
			}
		}
		return true;
	}
	
	private double numResourceInAreaBetween(Agent goodGuy, Agent badGuy){
		double resources = 0.0;
		for(int i = Math.min(goodGuy.getX(), badGuy.getX()); i < Math.max(goodGuy.getX(), badGuy.getX()); i++){
			for(int j = Math.min(goodGuy.getY(), badGuy.getY()); j < Math.max(goodGuy.getY(), badGuy.getY()); j++){
				if(this.board.isResource(i, j)){
					resources += 1;
				}
			}
		}
		return resources;
	}
	
	/**
	 * @return the sum of the distances to the closest enemy for each footman
	 */
 	private double distanceFromEnemy() {
		double utility = 0.0;
		for(Agent goodAgent : this.board.getAliveGoodAgents()){
			double value = Double.POSITIVE_INFINITY;
			for(Agent badAgent : this.board.getAliveBadAgents()){
				value = Math.min(this.board.distance(goodAgent, badAgent), value);
			}
			if(value != Double.POSITIVE_INFINITY){
				utility += value;
			}
		}
		return utility;
 	}
	
	private Agent getClosestEnemy(Agent goodAgent) {
		Agent closestEnemy = null;
		for(Agent badAgent : this.board.getAliveBadAgents()){
			if(closestEnemy == null){
				closestEnemy = badAgent;
			} else if(this.board.distance(goodAgent, badAgent) < this.board.distance(goodAgent, closestEnemy)){
				closestEnemy = badAgent;
			}
		}
		return closestEnemy;
	}

	/**
	 * Takes into account the current turn (good or bad) and generates children for 
	 * the current ply.
	 * 
	 * @return all of the possible children of this GameState
	 */
	public List<GameStateChild> getChildren() {
		Collection<Agent> agentsActiveThisTurn;
		if(ourTurn){
			agentsActiveThisTurn = this.board.getAliveGoodAgents();
		} else {
			agentsActiveThisTurn = this.board.getAliveBadAgents();
		}
		List<List<Action>> actionsForEachAgent = agentsActiveThisTurn.stream()
				.map(e -> getActionsForAgent(e))
				.collect(Collectors.toList());
		List<Map<Integer, Action>> actionMaps = enumerateActionCombinations(actionsForEachAgent);
		return enumerateChildrenFromActionMaps(actionMaps);
	}

	/**
	 * For a given agent generates all their possible moves:
	 * Move: NORTH, EAST, SOUTH, WEST or
	 * Attack any enemy close enough
	 * @param agent
	 * @return List of actions given agent could take
	 */
	private List<Action> getActionsForAgent(Agent agent){
		List<Action> actions = new ArrayList<Action>();
		for(Direction direction : Direction.values()){
			switch(direction){
			case NORTH :
			case EAST :
			case SOUTH :
			case WEST :
				int nextX = agent.getX() + direction.xComponent();
				int nextY = agent.getY() + direction.yComponent();
				if(this.board.isOnBoard(nextX, nextY) && this.board.isEmpty(nextX, nextY)){
					actions.add(Action.createPrimitiveMove(agent.getId(), direction));
				}
				break;
			default :
				break;
			}
		}
		for(Integer id : this.board.findAttackableAgents(agent)){
			actions.add(Action.createPrimitiveAttack(agent.getId(), id));
		}
		return actions;
	}

	/**
	 * Give a list of actions for every agent returns Maps from unitId to Action for each
	 * possible combination of actions for a pair of footmen or archers
	 */
	private List<Map<Integer, Action>> enumerateActionCombinations(List<List<Action>> allActions){
		List<Map<Integer, Action>> actionMaps = new ArrayList<Map<Integer, Action>>();
		if(allActions.isEmpty()){
			return actionMaps;
		}
		List<Action> actionsForFirstAgent = allActions.get(0);	
		for(Action actionForAgent : actionsForFirstAgent){
			if(allActions.size() == 1){
				Map<Integer, Action> actionMap = new HashMap<Integer, Action>();
				actionMap.put(actionForAgent.getUnitId(), actionForAgent);
				actionMaps.add(actionMap);
			} else {
				for(Action actionForOtherAgent : allActions.get(1)){
					Map<Integer, Action> actionMap = new HashMap<Integer, Action>();
					actionMap.put(actionForAgent.getUnitId(), actionForAgent);
					actionMap.put(actionForOtherAgent.getUnitId(), actionForOtherAgent);
					actionMaps.add(actionMap);
				}
			}
		}
		return actionMaps;
	}

	/**
	 * Given all Maps from unitId to Action that are possible for the current ply generate
	 * the GameStateChild for each Map 
	 */
	private List<GameStateChild> enumerateChildrenFromActionMaps(List<Map<Integer, Action>> actionMaps){
		List<GameStateChild> children = new ArrayList<GameStateChild>(25);
		for(Map<Integer, Action> actionMap : actionMaps){
			GameState child = new GameState(this);
			for(Action action : actionMap.values()){
				child.applyAction(action);
			}
			children.add(new GameStateChild(actionMap, child));
		}
		return children;
	}

	/**
	 * Applies a given action to this GameState
	 * @param action either a move or an attack
	 */
	private void applyAction(Action action) {
		if(action.getType().name().equals(ACTION_MOVE_NAME)){
			DirectedAction directedAction = (DirectedAction) action;
			this.board.moveAgentBy(directedAction.getUnitId(), directedAction.getDirection().xComponent(), directedAction.getDirection().yComponent());
		} else {
			TargetedAction targetedAction = (TargetedAction) action;
			Agent attacker = this.board.getAgent(targetedAction.getUnitId());
			Agent attacked = this.board.getAgent(targetedAction.getTargetId());
			this.board.attackAgent(attacker, attacked);
		}
	}

}
