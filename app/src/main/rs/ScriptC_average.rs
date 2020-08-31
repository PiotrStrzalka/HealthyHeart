#pragma rs java_package_name(com.automation.bear.healthyheartcamera)
#pragma rs_fp_relaxed
#pragma version(1)

// Use two global counters
static int totalSum = 0;
static int counter = 0;

// One kernel just sums up the channel red value and increments
// the global counter by 1 for each pixel
void __attribute__((kernel)) addRedChannel(uchar4 in){

 rsAtomicAdd(&totalSum, in.r);
 rsAtomicInc(&counter);

}

// This kernel places, inside the output allocation, the average
int __attribute__((kernel)) getTotalSum(int x){
    return totalSum/counter;
}

void resetCounters(){

    totalSum = 0;
    counter = 0;

}