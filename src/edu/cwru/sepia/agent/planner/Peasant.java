package edu.cwru.sepia.agent.planner;

public class Peasant {
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