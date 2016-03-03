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
	private double utility = 0.0;
	private boolean utilityCalculated = false;

	private class Board {
		private Square[][] board;
		private Map<Integer, Agent> guys = new HashMap<Integer, Agent>();
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
			guys.put(id, agent);
		}

		public void moveAgentBy(int id, int xOffset, int yOffset){
			moveAgentBy(id, xOffset, yOffset, guys);
		}

		private void moveAgentBy(int id, int xOffset, int yOffset, Map<Integer, Agent> agents){
			Agent agent = agents.get(id);
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
			attacked.hp = attacked.hp - attacker.attackDamage;
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

		public Agent getAgent(int i) {
			return guys.get(i);
		}
		
		public Collection<Agent> getAliveGoodAgents(){
			return guys.values().stream().filter(e -> e.isGood() && e.isAlive()).collect(Collectors.toList());
		}
		
		public Collection<Agent> getAliveBadAgents(){
			return guys.values().stream().filter(e -> !e.isGood() && e.isAlive()).collect(Collectors.toList());
		}

		public double distance(Agent agent1, Agent agent2) {
			return (Math.abs(agent1.getX() - agent2.getX()) + Math.abs(agent1.getY() - agent2.getY())) - 1;
		}

		public double attackDistance(Agent agent1, Agent agent2){
			return Math.floor(Math.hypot(Math.abs(agent1.getX() - agent2.getX()), Math.abs(agent1.getY() - agent2.getY())));
		}
		
		private List<Integer> findAttackableAgents(Agent agent) {
			List<Integer> attackable = new ArrayList<Integer>();
			for(Agent otherAgent : guys.values()){
				if(otherAgent.getId() != agent.getId() && (otherAgent.isGood() != agent.isGood()) && 
						attackDistance(agent, otherAgent) <= agent.attackRange){
					attackable.add(otherAgent.getId());
				}
			}
			return attackable;
		}
	}

	private class Square {
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

	private class Resource extends Square {
		public Resource(int id, int x, int y) {
			super(id, x, y);
		}		
	}

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

	public GameState(GameState gameState) {
		this.board = new Board(gameState.board.width, gameState.board.height);
		gameState.board.guys.values().stream().forEach( (e) -> {
			this.board.addAgent(e.getId(), e.getX(), e.getY(), e.hp, e.possibleHp, e.attackDamage, e.attackRange);			
		});

		gameState.board.resources.values().stream().forEach( (e) -> {		
			this.board.addResource(e.getId(), e.getX(), e.getY());		
		});
		this.ourTurn = !gameState.ourTurn;
	}

	public double getUtility() {
		if(this.utilityCalculated){
			return this.utility;
		}
		
		this.utility += haveGoodGuysUtility();
		this.utility += haveBadGuysUtility();
		this.utility += damageToEnemyUtility();
		this.utility += locationRelativeToEnemyUtility();
		this.utility += canAttackUtility();
				
		this.utilityCalculated = true;
		return this.utility;
	}
	
	private double locationRelativeToEnemyUtility() {
		if(this.board.resources.isEmpty() ||
			noResourcesAreInTheWay()){
			return distanceToClosestEnemyUtility() * -1;
		}
		return 0.0;
	}
	
	private boolean noResourcesAreInTheWay(){
		for(Agent goodGuy : this.board.getAliveGoodAgents()){
			for(Agent badGuy : this.board.getAliveBadAgents()){
				if(numOfResourceInSpaceBetween(goodGuy, badGuy) != 0){
					return false;
				}
			}
		}
		return true;
	}
	
	private double numOfResourceInSpaceBetween(Agent goodGuy, Agent badGuy){
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
	
	private double distanceToClosestEnemyUtility() {
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

	private double canAttackUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.getAliveGoodAgents()){
			utility += this.board.findAttackableAgents(agent).size();		
		}
		return utility;
	}

	private double damageToEnemyUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.getAliveBadAgents()){
			utility += agent.possibleHp - agent.hp;		
		}
		return utility;
	}

	private double haveGoodGuysUtility() {
		return this.board.getAliveGoodAgents().isEmpty() ? MIN_UTILITY : this.board.getAliveGoodAgents().size();
	}
	
	private double haveBadGuysUtility() {
		return this.board.getAliveBadAgents().isEmpty() ? MAX_UTILITY : this.board.getAliveBadAgents().size();
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

	public List<GameStateChild> getChildren() {
		List<List<Action>> allActions = new ArrayList<List<Action>>();
		Collection<Agent> agentsForTurn;
		if(ourTurn){
			agentsForTurn = this.board.getAliveGoodAgents();
		} else {
			agentsForTurn = this.board.getAliveBadAgents();
		}
		for(Agent agent : agentsForTurn){
			allActions.add(getActionsForAgent(agent));		
		}
		List<Map<Integer, Action>> actionMaps = enumerateActionOptions(allActions);
		return enumerateChildrenFromActionMaps(actionMaps);
	}

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

	private List<Map<Integer, Action>> enumerateActionOptions(List<List<Action>> allActions){
		List<Map<Integer, Action>> actionMaps = new ArrayList<Map<Integer, Action>>();
		if(allActions.isEmpty()){
			return actionMaps;
		}
		List<Action> actionsForAgent1 = allActions.get(0);	
		for(Action actionForAgent : actionsForAgent1){
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
