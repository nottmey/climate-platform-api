## Deployment

### Ions

Adapted from https://docs.datomic.com/cloud/ions-tutorial/push-and-deploy.html

```shell
# 1. make sure git status is clean

# 2. then do:
clojure -A:ion-dev '{:op :push}'

# 3. then use deploy command from push result, similar to this:
clojure -A:ion-dev '{:op :deploy :rev $REV :group $GROUP}'

# 4. monitor status with command from deploy result, similar to this:
clojure -A:ion-dev '{:op :deploy-status :execution-arn $EXECUTION_ARN}'
```
