package edu.cwru.sepia.agent.minimax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.util.Direction;

public class GameState {
	private Board board;
	private int width;
	private int height;
	
	private class Board {
		private Square[][] board;
		private Map<Integer, Agent> guys = new HashMap<Integer, Agent>();
		private Map<Integer, Resource> resources = new HashMap<Integer, Resource>();
		
		public Board(int x, int y){
			board = new Square[x][y];
		}
		
		public void addResource(int id, int x, int y){
			Resource resource = new Resource(id, x, y);
			board[x][y] = resource;
			resources.put(resource.id, resource);
		}
		
		public void addAgent(int id, int x, int y, boolean good, int hp, int attackDamage, int attackRange){
			Agent agent = new Agent(id, x, y, good, hp, attackDamage, attackRange);
			board[x][y] = agent;
			guys.put(id, agent);
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
		
	}

	private abstract class Square {
		public int id;
		public int x;
		public int y;
		
		public Square(int id, int x, int y){
			this.id = id;
		}
	}
	
	private class Agent extends Square {
		public boolean good;
		public int hp;
		public int attackDamage;
		public int attackRange;
		public Agent(int id, int x, int y, boolean good, int hp, int attackDamage, int attackRange) {
			super(id, x, y);
			this.good = good;
			this.hp = hp;
			this.attackDamage = attackDamage;
			this.attackRange = attackRange;
		}
	}
	
	private class Resource extends Square {
		public Resource(int id, int x, int y) {
			super(id, x, y);
		}		
	}

    public GameState(State.StateView state) {
    	this.width = state.getXExtent();
    	this.height = state.getYExtent();
    	this.board = new Board(width, height);
    	state.getAllUnits().stream().forEach( (e) -> {
    		if(e.getID() == 1 || e.getID() == 0){
    			this.board.addAgent(e.getID(), e.getXPosition(), e.getYPosition(), true, e.getHP(), e.getTemplateView().getRange(), e.getTemplateView().getBasicAttack());
    		} else {
    			this.board.addAgent(e.getID(), e.getXPosition(), e.getYPosition(), false, e.getHP(), e.getTemplateView().getRange(), e.getTemplateView().getBasicAttack());
    		}
    	});
    	
    	state.getAllResourceNodes().stream().forEach( (e) -> {
    		this.board.addResource(e.getID(), e.getXPosition(), e.getYPosition());
    	});
    }   

    public GameState(GameState gameState) {
    	this.width = gameState.width;
    	this.height = gameState.height;
    	this.board = new Board(width, height);
    	gameState.board.guys.values().stream().forEach( (e) -> {
    		if(e.id == 1 || e.id == 0){
    			this.board.addAgent(e.id, e.x, e.y, true, e.hp, e.attackRange, e.attackDamage);
    		} else {
    			this.board.addAgent(e.id, e.x, e.y, false, e.hp, e.attackRange, e.attackDamage);
    		}
    	});
    	
    	gameState.board.resources.values().stream().forEach( (e) -> {
    		this.board.addResource(e.id, e.x, e.y);
    	});

	}
    
    public double getUtility() {
    	return -1 * this.board.guys.size();
    }

    public List<GameStateChild> getChildren() {
    	List<GameStateChild> children = new ArrayList<GameStateChild>(25);
    	for(Agent unit : board.guys.values()){
    		if(unit.hp > 0){
	    		for(Direction direction : Direction.values()){
	    			int nextX = unit.x + direction.xComponent();
	    			int nextY = unit.y + direction.yComponent();
	    			if(nextX < width && nextX > -1 && nextY < height && nextY > -1){
	    				if(this.board.isEmpty(nextX, nextY)){
		    				Map<Integer, Action> map = new HashMap<Integer, Action>();
		    				Action action = Action.createPrimitiveMove(unit.id, direction);
		    				map.put(unit.id, action);
		    				GameState nextState = new GameState(this);
		    				nextState.board.moveAgentBy(unit.id, direction.xComponent(), direction.yComponent());
			    			switch(direction){
			    			case NORTH :
			    			case EAST :
			    			case SOUTH :
			    			case WEST :
			    				children.add(new GameStateChild(map, nextState));
			    				break;
			    			default :
			    				break;
			    			}
	    				}
	    			}
	    		}
	    		List<Integer> attackable = idsCanAttack(unit);
	    		if(!attackable.isEmpty()){
	    			for(Integer id : attackable){
	    				Map<Integer, Action> map = new HashMap<Integer, Action>();
	    				Action action = Action.createPrimitiveAttack(unit.id, id);
	    				map.put(unit.id, action);
	    				GameState nextState = new GameState(this);
	    				Agent other = this.board.guys.get(id);
	    				other.hp = other.hp - unit.attackDamage;
	    				children.add(new GameStateChild(map, nextState));
	    			}
	    		}
    		}
    	}
    	return children;
    }

	private List<Integer> idsCanAttack(Agent unit) {
		List<Integer> attackable = new ArrayList<Integer>();
		for(Agent agent : this.board.guys.values()){
			if(agent.id != unit.id && (Math.abs(unit.x - agent.x) + Math.abs(unit.y - agent.y)) < unit.attackRange){
				attackable.add(agent.id);
			}
		}
		return attackable;
	}
}
