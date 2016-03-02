package edu.cwru.sepia.agent.minimax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.util.Direction;

public class GameState {
	private Board board;
	private boolean ourTurn;
	private double utility = 0.0;

	private class Board {
		private Square[][] board;
		private int originalNumGood;
		private int originalNumBad;
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
			resources.put(resource.id, resource);
		}

		public void addAgent(int id, int x, int y, int hp, int possibleHp, int attackDamage, int attackRange){
			Agent agent = new Agent(id, x, y, hp, possibleHp, attackDamage, attackRange);
			board[x][y] = agent;
			guys.put(id, agent);
			if(agent.isGood()){
				originalNumGood++;
			} else {
				originalNumBad++;
			}
		}

		public void moveAgentBy(int id, int xOffset, int yOffset){
			moveAgentBy(id, xOffset, yOffset, guys);
		}

		private void moveAgentBy(int id, int xOffset, int yOffset, Map<Integer, Agent> agents){
			Agent agent = agents.get(id);
			int currentX = agent.x;
			int currentY = agent.y;
			int nextX = currentX + xOffset;
			int nextY = currentY + yOffset;
			board[currentX][currentY] = null;
			agent.x = nextX;
			agent.y = nextY;
			board[nextX][nextY] = agent;
		}

		public boolean isEmpty(int x, int y){
			return board[x][y] == null;
		}

		public boolean isOnBoard(int x, int y){
			return x >= 0 && x < width && y >= 0 && y < height; 
		}

		public Agent getAgent(int i) {
			return guys.get(i);
		}

		public double distance(Agent agent1, Agent agent2) {
			return (Math.abs(agent1.x - agent2.x) + Math.abs(agent1.y - agent2.y)) - 1;
		}
		
		public double attackDistance(Agent agent1, Agent agent2){
			return Math.min(Math.abs(agent1.x - agent2.x), Math.abs(agent1.y - agent2.y));
		}
	}

	private abstract class Square {
		public int id;
		public int x;
		public int y;

		public Square(int id, int x, int y){
			this.id = id;
			this.x = x;
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
			return id == 0 || id == 1;
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
			this.board.addAgent(e.id, e.x, e.y, e.hp, e.possibleHp, e.attackDamage, e.attackRange);			
		});

		gameState.board.resources.values().stream().forEach( (e) -> {		
			this.board.addResource(e.id, e.x, e.y);		
		});
		this.ourTurn = !gameState.ourTurn;
	}

	public double getUtility() {
		if(this.utility != 0.0){
			return this.utility;
		}
		double score = 0.0;

		score += haveGoodGuysUtility();
		score += haveBadGuysUtility();
		score += distanceFromEnemeyUtility();
		score += damageToEnemyUtility(); 
				
		this.utility = score;
		return this.utility;
	}

	private double damageToEnemyUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.guys.values()){
			if(!agent.isGood() && agent.isAlive()){
				utility += agent.possibleHp - agent.hp;
			}
		}
		return utility * 10;
	}

	private double haveGoodGuysUtility() {
		double utility = 0.0;
		boolean atLeastOne = false;
		for(Agent agent : this.board.guys.values()){
			if(agent.isGood() && agent.isAlive()){
				utility += 100;
				atLeastOne = true;
			}
		}
		if(!atLeastOne){
			return Double.NEGATIVE_INFINITY;
		}
		return utility;
	}
	
	private double haveBadGuysUtility() {
		double utility = 0.0;
		boolean atLeastOne = false;
		for(Agent agent : this.board.guys.values()){
			if(!agent.isGood()){
				utility -= 100;
				atLeastOne = true;
			}
		}
		if(!atLeastOne){
			return Double.POSITIVE_INFINITY;
		}
		return utility;
	}
	
	private double distanceFromEnemeyUtility() {
		double utility = 0.0;
		for(Agent agent : this.board.guys.values()){
			if(agent.isGood() && agent.isAlive()){
				utility += Math.min(this.board.distance(agent, this.board.getAgent(3)), this.board.distance(agent, this.board.getAgent(4)) * 100);
			}
		}
		return utility * -1;
	}

	public List<GameStateChild> getChildren() {
		List<List<Action>> allActions = new ArrayList<List<Action>>();
		for(Agent agent : board.guys.values()){
			if(agent.isAlive() && ((ourTurn && agent.isGood()) || (!ourTurn && !agent.isGood()))){
				allActions.add(getActionsForAgent(agent));
			}
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
				int nextX = agent.x + direction.xComponent();
				int nextY = agent.y + direction.yComponent();
				if(this.board.isOnBoard(nextX, nextY) && this.board.isEmpty(nextX, nextY)){
					actions.add(Action.createPrimitiveMove(agent.id, direction));
				}
				break;
			default :
				break;
			}
		}
		for(Integer id : findAttackableAgents(agent)){
			actions.add(Action.createPrimitiveAttack(agent.id, id));
		}
		return actions;
	}

	private List<Integer> findAttackableAgents(Agent agent) {
		List<Integer> attackable = new ArrayList<Integer>();
		for(Agent otherAgent : this.board.guys.values()){
			if(otherAgent.id != agent.id && (otherAgent.isGood() != agent.isGood()) && 
					this.board.distance(agent, otherAgent) <= agent.attackRange){
				attackable.add(otherAgent.id);
			}
		}
		return attackable;
	}

	private List<Map<Integer, Action>> enumerateActionOptions(List<List<Action>> allActions){
		List<Map<Integer, Action>> actionMaps = new ArrayList<Map<Integer, Action>>();
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
		if(action.getType().name().equals(Action.createPrimitiveMove(0, null).getType().name())){
			DirectedAction directedAction = (DirectedAction) action;
			this.board.moveAgentBy(directedAction.getUnitId(), directedAction.getDirection().xComponent(), directedAction.getDirection().yComponent());
		} else {
			TargetedAction targetedAction = (TargetedAction) action;
			Agent attacker = this.board.guys.get(targetedAction.getUnitId());
			Agent other = this.board.guys.get(targetedAction.getTargetId());
			other.hp = other.hp - attacker.attackDamage;
		}
	}

}
