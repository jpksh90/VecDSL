import rospy
from std_msgs.msg import Empty
from sensor_msgs.msg import Range

distance_value = 0

def distance_callback(msg):
    global distance_value
    distance_value = msg.range

def get_distance():
    return distance_value

move_pub = rospy.Publisher('move', Empty, queue_size=10)
turn_pub = rospy.Publisher('turn', Empty, queue_size=10)
stop_pub = rospy.Publisher('stop', Empty, queue_size=10)
distance_sub = rospy.Subscriber('distance', Range, distance_callback)

def main():
    x = 5
    if x > 0:
        rospy.loginfo('move')  # TODO: Replace with actual move command
    while get_distance() < 10:
        rospy.loginfo('turn')  # TODO: Replace with actual turn command
    rospy.loginfo('stop')  # TODO: Replace with actual stop command
if __name__ == '__main__':
    rospy.init_node('robot_dsl_node')
    main()
