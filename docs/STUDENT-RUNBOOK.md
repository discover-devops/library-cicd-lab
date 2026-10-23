# Student Runbook: End to End CI/CD with GitHub Actions on AWS

Discover DevOps | Build Automate Architect

You will push code to GitHub and watch it travel through compile, security scan, tests, quality gate, Docker build and image scan, Docker Hub, and finally a live deployment on Amazon EKS. Every step has three parts:

- **Context**: where you are in the pipeline and why this step exists
- **Concept**: the one idea to understand before you type anything
- **Lab**: copy and paste steps, then what you should see

---

## Step 0: Before you start

### Context
This runbook is written so you can follow it top to bottom without guessing. Commands are exact. Copy them as they are.

### Concept
**Three different places are used in this lab. Always check the tag before you run a command.**

| Tag | Where you run it |
|---|---|
| **[LAPTOP]** | The terminal inside VS Code on your own computer (on Windows use Git Bash) |
| **[EC2]** | The terminal connected to your EC2 server over SSH |
| **[BROWSER]** | GitHub, Docker Hub, SonarQube or the AWS console in a web browser |

### Lab

**Values used in this runbook**

| What | Value |
|---|---|
| GitHub account | `discover-devops` |
| GitHub repository | `library-cicd-lab` |
| Docker Hub account | `discoverdevops` |
| Docker Hub repository | `discoverdevops/library-api` |
| AWS region | `ap-south-1` (Mumbai) |
| EKS cluster name | `discover-eks` |

If you are using your own GitHub and Docker Hub accounts, do a find and replace in this file: change `discover-devops` to your GitHub username and `discoverdevops` to your Docker Hub username. Change nothing else.

**You need before you begin**
- A GitHub account and a Docker Hub account
- An AWS account with admin access
- Git, VS Code and the AWS CLI installed on your laptop

**[LAPTOP] Check your tools:**

```bash
git --version
aws --version
aws sts get-caller-identity
aws configure get region
```

You should see version numbers and your AWS account id. If the last command does not print `ap-south-1`, run:

```bash
aws configure set region ap-south-1
```

**Using temporary credentials (AWS SSO or a work account)?** If your AWS portal gives you `export AWS_ACCESS_KEY_ID=...`, `export AWS_SECRET_ACCESS_KEY=...` and `export AWS_SESSION_TOKEN=...` lines, paste all three lines into this same Git Bash window, then run:

```bash
export AWS_DEFAULT_REGION=ap-south-1
aws sts get-caller-identity
```

Three things to know:
1. These variables exist only in that one terminal window. Paste them again in any new window.
2. They expire, usually after 1 to 12 hours. When commands fail with `ExpiredToken` or `InvalidClientTokenId`, copy fresh ones from the portal.
3. Do not run `aws configure` when you use them. Never paste real keys into chat, email or Git.

**Cost warning:** EKS, a NAT gateway, a load balancer and two t3.medium servers cost money every hour. Plan to finish the lab and run **Step 17 (Cleanup)** the same day.

---

## Step 1: Get the project

### Context
Everything starts with code in a GitHub repository. The pipeline lives inside the repository, so you need your own copy where you are allowed to add workflows, secrets and a runner.

### Concept
**A fork is your own copy of someone else's repository.**

**Default Behavior**
- A new fork has GitHub Actions switched off, and none of the original repository's secrets or variables come with it.

**What Changes**
- You enable Actions once, and you add your own secrets and variables in later steps.

**Why It Matters**
- Secrets never travel with code. That is the safe behavior, and it is why every learner configures their own credentials.

### Lab

**[BROWSER]**
1. Open `https://github.com/discover-devops/library-cicd-lab`.
2. Click **Fork** (top right), then **Create fork**. (If you are the owner of this repository, skip the fork.)
3. Open the **Actions** tab of your copy. If you see a green button, click **I understand my workflows, go ahead and enable them**.

**[LAPTOP]**

```bash
git clone https://github.com/discover-devops/library-cicd-lab.git
cd library-cicd-lab
git config user.name "Your Name"
git config user.email "you@example.com"
ls
```

**Expected:** you see `Dockerfile`, `k8s`, `lab-steps`, `pom.xml`, `scripts`, `src`.

Look at what the lab-steps folder holds. Each file is the workflow at a different stage of the lab:

```bash
ls lab-steps
```

---

## Step 2: Your first workflow (compile)

### Context
This is Job 1 of 6. Before testing or deploying anything, we check that the code at least compiles.

### Concept
**A workflow is a YAML file in `.github/workflows/` that GitHub runs when an event happens.**

**Default Behavior**
- Without a workflow file, a push to GitHub does nothing but store the code.

**What Changes**
- With `on: push: branches: [main]`, every push to `main` starts the workflow. A workflow contains jobs, a job contains steps, and a step either runs a command (`run:`) or uses a ready-made action (`uses:`).

**Why It Matters**
- This is the same idea as a Jenkinsfile, but the pipeline definition is stored with the code and triggered by GitHub itself.

The mental model to remember: **Event, Workflow, Job, Runner, Step, Action or command.**

### Lab

**[LAPTOP]**

```bash
mkdir -p .github/workflows
cp lab-steps/step-01-compile.yaml .github/workflows/cicd.yaml
cat .github/workflows/cicd.yaml
git add .
git commit -m "step 1: compile job"
git push origin main
```

**[BROWSER]** Open your repository, click the **Actions** tab, and click the running workflow.

**Expected:** a job named **Compile** with a green tick. Click it and open **Compile with Maven**; the log ends with `BUILD SUCCESS`.

This job ran on `ubuntu-latest`, a temporary machine GitHub gave you for a few minutes.

---

## Step 3: Security scan on a GitHub-hosted runner

### Context
Job 2 of 6. Code can be correct and still be unsafe: a committed password, or a library with a known vulnerability. Two scanners check this before anything else happens.

### Concept
**`needs: compile` makes a job wait for another job to succeed.**

**Default Behavior**
- Jobs in a workflow run at the same time, in parallel.

**What Changes**
- With `needs: compile`, the security job starts only after compile succeeds. If compile fails, the security job is skipped.

**Why It Matters**
- This is how you build a pipeline order and a quality gate. Failing early saves time and prevents unsafe code moving forward.

**What the two scanners do**
- **Gitleaks** reads your Git history and fails the job if it finds passwords, API keys or tokens.
- **Trivy (filesystem mode)** reads `pom.xml` and reports libraries with known vulnerabilities. It looks each library up in the local Maven cache, so the job first runs `mvn dependency:resolve` to fill that cache. In this step Trivy only reports; it does not fail the job.

### Lab

**[LAPTOP]**

```bash
cp lab-steps/step-02-security-hosted.yaml .github/workflows/cicd.yaml
git add .
git commit -m "step 2: add security scan on hosted runner"
git push origin main
```

**[BROWSER]** Actions tab, open the newest run.

**Expected:** two jobs, **Compile** then **Security scan**, both green.

Notice the **Install Trivy** and **Install Gitleaks** steps. A hosted runner is brand new every time, so the tools are installed on every single run. We will fix that in Step 8.

Also notice **Download Maven dependencies**. Without it, Trivy asks Maven Central for every library from GitHub's shared network address, and Maven Central can answer `429 Too Many Requests`. Filling the local cache first avoids that.

**See the reports:** scroll to the bottom of the run page, find **Artifacts**, and download **security-reports**. It contains `gitleaks-report.json` and `trivy-fs-report.json`.

---

## Step 4: Launch your EC2 runner

### Context
Hosted runners are temporary and you cannot log in to them. For the rest of the lab we need a persistent machine that can hold tools, run SonarQube and talk to our EKS cluster. You will create that machine in AWS.

### Concept
**A self-hosted runner is a server you own that GitHub sends jobs to.**

**Default Behavior**
- GitHub-hosted runners are created and deleted by GitHub for each job. Nothing is kept.

**What Changes**
- A self-hosted runner is a long-lived machine. Installed tools, Docker images and running services stay. You manage and pay for it.

**Why It Matters**
- Real teams use it to reach private networks, run heavy builds and keep control of the build environment.
- The runner only makes **outbound** connections to GitHub. That is why we open no inbound ports for it, only SSH and SonarQube for you.

### Lab

**[LAPTOP]** Run these blocks in order, in the same terminal window.

```bash
export MSYS_NO_PATHCONV=1   # Windows Git Bash: stops /aws/... and /dev/sda1 being turned into C:/ paths. Harmless on Mac/Linux
export AWS_REGION=ap-south-1
MYIP=$(curl -s https://checkip.amazonaws.com)
echo "Your public IP is $MYIP"
```

```bash
AMI=$(aws ssm get-parameter --region $AWS_REGION \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value --output text)
echo "AMI: $AMI"
```

```bash
aws ec2 create-key-pair --region $AWS_REGION --key-name gha-runner-key \
  --query KeyMaterial --output text > gha-runner-key.pem
chmod 400 gha-runner-key.pem
```

```bash
SG=$(aws ec2 create-security-group --region $AWS_REGION --group-name gha-runner-sg \
  --description "GitHub runner lab" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --region $AWS_REGION --group-id $SG \
  --protocol tcp --port 22 --cidr $MYIP/32
aws ec2 authorize-security-group-ingress --region $AWS_REGION --group-id $SG \
  --protocol tcp --port 9000 --cidr $MYIP/32
echo "Security group: $SG"
```

Create an IAM role for the server. This gives the EC2 machine its own AWS access, so **no access keys ever have to be stored on it**:

```bash
aws iam create-role --role-name gha-runner-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
aws iam attach-role-policy --role-name gha-runner-role \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
aws iam create-instance-profile --instance-profile-name gha-runner-profile
aws iam add-role-to-instance-profile --instance-profile-name gha-runner-profile \
  --role-name gha-runner-role
sleep 15
```

The role has full admin rights only because this is a short lab that creates an EKS cluster. Step 17 deletes it.

Now launch the server with that role attached:

```bash
INSTANCE_ID=$(aws ec2 run-instances --region $AWS_REGION --image-id $AMI \
  --instance-type t3.medium --key-name gha-runner-key --security-group-ids $SG \
  --iam-instance-profile Name=gha-runner-profile \
  --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3}' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=gha-runner-library}]' \
  --query 'Instances[0].InstanceId' --output text)
echo "Instance: $INSTANCE_ID"
aws ec2 wait instance-running --region $AWS_REGION --instance-ids $INSTANCE_ID
PUBLIC_IP=$(aws ec2 describe-instances --region $AWS_REGION --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "Public IP: $PUBLIC_IP"
```

**Write down the Public IP.** You will use it for SSH and for SonarQube.

Connect (wait about 30 seconds after the instance is running):

```bash
ssh -o StrictHostKeyChecking=accept-new -i gha-runner-key.pem ubuntu@$PUBLIC_IP
```

**Expected:** a prompt that looks like `ubuntu@ip-172-31-...:~$`. From now on, **[EC2]** commands run here.

**Prefer clicking?** In the console, EC2, Launch instance, name `gha-runner-library`, Ubuntu Server 24.04, `t3.medium`, a new key pair, 30 GB storage, and a security group allowing SSH (22) and TCP 9000 from My IP only.

---

## Step 5: Install the tools on the runner

### Context
A self-hosted runner starts empty. Every tool the pipeline calls (Maven, Docker, Trivy, Gitleaks, AWS CLI, kubectl, eksctl) must be installed on it once.

### Concept
**Install once, use in every job.**

**Default Behavior**
- On a hosted runner, you install tools inside the workflow, on every run (you saw this in Step 3).

**What Changes**
- On your own runner, you install tools one time and the workflow just uses them. Pipelines run faster and the workflow file gets shorter.

**Why It Matters**
- Teams keep a "runner setup script" like this one so any new runner can be rebuilt in minutes.

### Lab

**[EC2]**

```bash
git clone https://github.com/discover-devops/library-cicd-lab.git
bash library-cicd-lab/scripts/install-runner-tools.sh
```

This takes 3 to 5 minutes. At the end it prints the installed versions.

**Important:** the script added your user to the `docker` group. That only takes effect in a **new** login session. Log out and back in:

**[EC2]**

```bash
exit
```

**[LAPTOP]** (reconnect; the variable `PUBLIC_IP` still exists in the same terminal window. If you opened a new window, type the IP you wrote down.)

```bash
ssh -i gha-runner-key.pem ubuntu@$PUBLIC_IP
```

**[EC2] Verify:**

```bash
mvn -version
docker run --rm hello-world
trivy --version
gitleaks version
aws --version
kubectl version --client
eksctl version
```

**Expected:** every command prints a version, and `hello-world` prints "Hello from Docker!" without needing `sudo`.

---

## Step 6: Register the runner with GitHub

### Context
The server exists but GitHub does not know about it yet. Registration connects the two.

### Concept
**Registration is a handshake: a one-time token proves this server belongs to your repository, then the runner keeps asking GitHub "any work for me?"**

**Default Behavior**
- A workflow that says `runs-on: self-hosted` waits forever if no runner exists, showing "Waiting for a runner to pick up this job".

**What Changes**
- After registration, the runner shows as **Idle**, and any job with `runs-on: self-hosted` is sent to it.

**Why It Matters**
- No inbound firewall rule is needed, because the runner always calls out to GitHub.

### Lab

**[BROWSER]**
1. Open your repository, then **Settings**, then **Actions**, then **Runners**.
2. Click **New self-hosted runner**.
3. Choose **Linux** and **x64**. Keep this page open. It shows commands under **Download**, **Configure**.

**[EC2]** Copy the commands from the GitHub page and run them one block at a time, in this order:
1. The **Download** block (it starts with `mkdir actions-runner && cd actions-runner`, then `curl ...`, a checksum line and `tar xzf ...`).
2. The **Configure** block (it starts with `./config.sh --url ... --token ...`).

Answer the prompts like this:

| Prompt | What to type |
|---|---|
| Runner group | Press Enter |
| Name of runner | `runner-one` |
| Additional labels | Press Enter (GitHub already adds `self-hosted`, `Linux`, `X64`) |
| Work folder | Press Enter |

Now install it as a service so it keeps running after you close SSH:

**[EC2]**

```bash
sudo ./svc.sh install ubuntu
sudo ./svc.sh start
sudo ./svc.sh status
```

**Expected:** `status` shows **active (running)**. In the browser, refresh **Settings, Actions, Runners**: `runner-one` shows **Idle**.

---

## Step 7: AWS access and EKS cluster (start it now)

### Context
Job 6 deploys to a Kubernetes cluster on AWS (EKS). Creating one takes 15 to 20 minutes, so we start it now and continue with the other steps while it builds.

### Concept
**The identity that creates an EKS cluster becomes its administrator.**

**Default Behavior**
- Nobody can talk to a new cluster except the AWS identity that created it.

**What Changes**
- We create the cluster from the runner machine, using the IAM role attached to the server. The deploy job runs on the same machine with the same role, so it is allowed to deploy.

**Why It Matters**
- A role attached to the server means no access keys sit on the machine, which is the safer pattern. The lab role has full admin rights only because the lab is short lived; in production you would grant far less. Never commit AWS keys to Git.

### Lab

**[EC2]** The server already has AWS access through the role you attached in Step 4, so no keys are needed. Set the region and check:

```bash
export AWS_DEFAULT_REGION=ap-south-1
echo 'export AWS_DEFAULT_REGION=ap-south-1' >> ~/.bashrc
aws sts get-caller-identity
```

**Expected:** the output contains `assumed-role/gha-runner-role`. Now start the cluster in the background:

**[EC2]**

```bash
cd ~/library-cicd-lab
nohup bash scripts/create-eks-cluster.sh > eks-create.log 2>&1 &
sleep 5
tail -n 5 eks-create.log
```

Check progress any time with `tail -n 5 ~/library-cicd-lab/eks-create.log`. **Do not wait for it. Continue to Step 8.**

You will know it is finished when the log ends with a table of nodes, and:

```bash
kubectl get nodes
```

shows one node with status **Ready**.

---

## Step 8: Move the pipeline to your runner

### Context
The pipeline runs on GitHub's temporary machine today. Now it moves to your own runner.

### Concept
**`runs-on: self-hosted` sends the job to a runner you registered.**

**Default Behavior**
- `runs-on: ubuntu-latest` uses a fresh GitHub machine every time.

**What Changes**
- `runs-on: self-hosted` uses `runner-one`. The two install steps for Trivy and Gitleaks are deleted, because both are already on the machine.

**Why It Matters**
- Faster runs, shorter workflow file, and full control of the environment.

### Lab

**[LAPTOP]**

```bash
cp lab-steps/step-03-self-hosted.yaml .github/workflows/cicd.yaml
git add .
git commit -m "step 3: run on self-hosted runner"
git push origin main
```

**[BROWSER]** Actions tab, open the newest run, then click a job.

**Expected:** both jobs are green. In the job log, the first lines show **Runner name: 'runner-one'**.

**[EC2]** (optional, to see it live)

```bash
sudo journalctl -u 'actions.runner.*' -n 20 --no-pager
```

---

## Step 9: Unit tests and coverage

### Context
Job 3 of 6. Automated tests are the safety net. If a test fails, nothing after it runs.

### Concept
**A failing test stops the pipeline. That is the point.**

**Default Behavior**
- Without a test job, broken behavior is only discovered by users.

**What Changes**
- The `test` job runs `mvn test`. JaCoCo records which lines the tests touched ("coverage"). Test and coverage reports are saved as an artifact.

**Why It Matters**
- Coverage is also the input for the SonarQube quality gate in Step 11.

### Lab

**[LAPTOP]**

```bash
cp lab-steps/step-04-test.yaml .github/workflows/cicd.yaml
git add .
git commit -m "step 4: add unit tests and coverage"
git push origin main
```

**[BROWSER]** Actions tab, open the newest run.

**Expected:** three jobs in order: **Compile**, **Security scan**, **Unit tests and coverage**, all green. In the test job log you see `Tests run: ... Failures: 0`.

Download the artifact **test-and-coverage-reports**, unzip it, and open `site/jacoco/index.html` in your browser to see the coverage report.

---

## Step 10: Set up SonarQube

### Context
Job 4 needs a SonarQube server to send its analysis to. We run it as a container on your runner machine.

### Concept
**SonarQube reads your code and reports bugs, vulnerabilities, code smells, coverage and duplication, then applies a "quality gate".**

**Default Behavior**
- Without it, code quality is judged by opinion in code reviews.

**What Changes**
- A quality gate is a pass or fail rule set, such as "no new bugs and at least 80 percent coverage on new code". The pipeline fails when the gate fails.

**Why It Matters**
- It turns "good code" into something measurable and enforceable, in any language.

### Lab

**[EC2]**

```bash
cd ~/library-cicd-lab
bash scripts/start-sonarqube.sh
```

Wait two minutes, then check:

```bash
sudo docker logs --tail 5 sonar
curl -s http://localhost:9000/api/system/status
```

**Expected:** the status shows `"status":"UP"`. If it shows `STARTING`, wait one more minute and repeat.

**[EC2]** find your server's public IP:

```bash
curl -s https://checkip.amazonaws.com
```

**[BROWSER]**
1. Open `http://<that IP>:9000`.
2. Log in with username `admin` and password `admin`. SonarQube asks you to set a new password. Choose one and remember it.
3. Click your avatar (top right), then **My Account**, then **Security**.
4. Under **Generate Tokens**, name it `github-actions`, click **Generate**, and **copy the token now**. You cannot see it again.

**[BROWSER]** Store it in GitHub. In your repository: **Settings**, **Secrets and variables**, **Actions**.
1. Tab **Secrets**, **New repository secret**: name `SONAR_TOKEN`, value the token you copied.
2. Tab **Variables**, **New repository variable**: name `SONAR_HOST_URL`, value `http://localhost:9000`.

Why `localhost`? The scan runs on the same machine as SonarQube, so it can reach it directly. It keeps working even if the public IP changes.

**Secret or variable?** A secret is encrypted and hidden in logs (use it for tokens and passwords). A variable is plain text (use it for names and URLs).

---

## Step 11: Package, SonarQube scan and quality gate

### Context
Job 4 of 6. It builds the deployable JAR file, uploads it for later jobs, and asks SonarQube whether the code is good enough to continue.

### Concept
**An artifact is a file one job saves so that another job can use it.**

**Default Behavior**
- Every job starts with a clean workspace. A file built in one job is gone in the next.

**What Changes**
- `upload-artifact` saves `library-api.jar` to GitHub. The Docker job in Step 13 downloads it with `download-artifact`.

**Why It Matters**
- It guarantees that the file you tested and scanned is the exact file you ship. This is "build once, deploy the same thing".

**The quality gate**
- `-Dsonar.qualitygate.wait=true` makes the pipeline wait for SonarQube's verdict. If the gate fails, the job fails and the Docker and deploy jobs are skipped.

### Lab

**[LAPTOP]**

```bash
cp lab-steps/step-05-sonar-package.yaml .github/workflows/cicd.yaml
git add .
git commit -m "step 5: package, sonarqube scan, quality gate"
git push origin main
```

**[BROWSER]** Actions tab, open the newest run. The new job is **Package, SonarQube analysis and quality gate**.

**Expected:** all four jobs are green. At the bottom of the run page, the Artifacts list now includes **app-jar**.

**[BROWSER]** Open `http://<EC2 public IP>:9000`, click the project **Library API**.

**Expected:** **Quality Gate: Passed**, with coverage, bugs, vulnerabilities and code smells shown.

---

## Step 12: Docker Hub setup

### Context
Job 5 pushes your container image to a registry, and Kubernetes pulls it from there. We use Docker Hub.

### Concept
**A registry is a storage place for container images, and the repository name decides where an image goes.**

**Default Behavior**
- Images exist only on the machine that built them.

**What Changes**
- After `docker push discoverdevops/library-api`, anyone with access (including your EKS cluster) can pull it.

**Why It Matters**
- The Kubernetes nodes are different machines, so they need a registry to fetch the image. We make the repository **public** so the nodes can pull without credentials.

### Lab

**[BROWSER]** Docker Hub, `https://hub.docker.com`, sign in as `discoverdevops`.
1. **Repositories**, **Create repository**. Namespace `discoverdevops`, name `library-api`, visibility **Public**, **Create**.
2. Click your avatar, **Account settings**, **Personal access tokens**, **Generate new token**. Description `github-actions`, permissions **Read & Write**, **Generate**. **Copy the token now.**

**[BROWSER]** In GitHub: **Settings**, **Secrets and variables**, **Actions**.
1. Tab **Variables**, **New repository variable**: name `DOCKER_HUB_USERNAME`, value `discoverdevops`.
2. Tab **Secrets**, **New repository secret**: name `DOCKER_HUB_TOKEN`, value the Docker Hub token.

**Check your four settings.** You should now have:

| Name | Type |
|---|---|
| `SONAR_TOKEN` | Secret |
| `DOCKER_HUB_TOKEN` | Secret |
| `SONAR_HOST_URL` | Variable (`http://localhost:9000`) |
| `DOCKER_HUB_USERNAME` | Variable (`discoverdevops`) |

---

## Step 13: Build, scan and push the Docker image

### Context
Job 5 of 6. The JAR becomes a container image. The image is scanned, and only if it is clean is it pushed.

### Concept
**Tag images with the Git commit SHA, not only `latest`.**

**Default Behavior**
- `latest` is just a moving label. You cannot tell which code it contains, and Kubernetes may not restart pods when `latest` changes.

**What Changes**
- The job tags every image twice: `:<commit sha>` (an exact, unique version) and `:latest`. The deploy job uses the SHA tag.

**Why It Matters**
- You can always answer "which commit is running in production?", and you can roll back to an exact earlier version.

**The image scan is a real gate**
- `trivy image --severity CRITICAL --exit-code 1` fails the job if the image has a critical, fixable vulnerability. The push step never runs, so an unsafe image never reaches the registry.

### Lab

**[LAPTOP]**

```bash
cp lab-steps/step-06-docker.yaml .github/workflows/cicd.yaml
git add .
git commit -m "step 6: docker build, image scan, push"
git push origin main
```

**[BROWSER]** Actions tab, open the newest run.

**Expected:** five green jobs; the last is **Build image, scan it, push to Docker Hub**.

**[BROWSER]** Docker Hub, `discoverdevops/library-api`, **Tags**.

**Expected:** two tags: `latest` and a long hexadecimal tag that matches your commit SHA.

---

## Step 14: Deploy to EKS

### Context
Job 6 of 6, the finish line. Kubernetes pulls the image from Docker Hub and runs two copies of it, behind a public load balancer.

### Concept
**`environment: production` names the place you deploy to, so GitHub can track it and protect it.**

**Default Behavior**
- Any job can deploy anywhere and nobody is asked.

**What Changes**
- Jobs that use `environment: production` appear under the repository's **Environments** page with a deployment history. Teams can add required reviewers there, so a person must approve before the job runs.

**Why It Matters**
- It is the normal way to add a human approval before production.

**What the deploy job does**
1. `aws eks update-kubeconfig` points `kubectl` at your cluster.
2. `sed` swaps `IMAGE_PLACEHOLDER` and `VERSION_PLACEHOLDER` in `k8s/deployment.yaml` for the exact image and commit SHA.
3. `kubectl apply` creates or updates the Deployment and the LoadBalancer Service.
4. `kubectl rollout status` waits until the new pods are healthy.

### Lab

**[EC2]** First confirm the cluster from Step 7 is ready:

```bash
kubectl get nodes
```

**Expected:** one node, status **Ready**. If the command fails, the cluster is still building: wait and check `tail -n 5 ~/library-cicd-lab/eks-create.log`.

**[LAPTOP]**

```bash
cp lab-steps/step-07-deploy-final.yaml .github/workflows/cicd.yaml
git add .
git commit -m "step 7: deploy to EKS"
git push origin main
```

**[BROWSER]** Actions tab, open the newest run. When all six jobs are green, open the run's **Summary** page: it shows **Application URL: http://...elb.amazonaws.com**.

**[EC2]** (alternative way to find the URL)

```bash
kubectl get svc library-api
kubectl get pods
```

**Expected:** two pods in **Running** state, and the service shows an `EXTERNAL-IP` hostname.

---

## Step 15: Prove it works

### Context
A pipeline you have not watched do its job is just a diagram. Now you make a real code change and watch it go to production on its own.

### Concept
**Continuous delivery means a commit is the only thing a human does. Everything else is automatic.**

### Lab

**[LAPTOP]** Copy the application URL from the run summary. Replace `<URL>` below with it (without a trailing slash).

```bash
curl http://<URL>/
curl http://<URL>/api/info
curl http://<URL>/api/books
```

If the URL does not respond, wait two to three minutes: a new load balancer's DNS name takes a little while to start working.

**Expected from `/api/info`:** `"version"` is the long commit SHA, and `"host"` is a pod name. Run the `/api/info` command five times and you will see **two different pod names**: the load balancer is sharing traffic.

Confirm the version is your latest commit:

```bash
git rev-parse HEAD
```

**Now make a change.** In VS Code open `src/main/java/com/discoverdevops/library/InfoController.java`. Find this text:

```
Discover DevOps Library API is running
```

and change it to:

```
Discover DevOps Library API version 2 is running
```

Save the file, then:

```bash
git add .
git commit -m "change welcome message"
git push origin main
```

**[BROWSER]** Watch the Actions run go through all six jobs. When it finishes (about 6 to 10 minutes):

**[LAPTOP]**

```bash
curl http://<URL>/
curl http://<URL>/api/info
git rev-parse HEAD
```

**Expected:** the new message appears and the `version` matches your new commit SHA. You just did an automated deployment.

---

## Step 16: Break it on purpose (optional but valuable)

### Context
Pipelines are only useful if they stop bad changes. Break three things on purpose and watch each gate work.

### Concept
**A good pipeline fails loudly and early, and shows you exactly where.**

### Lab

**Exercise A: a failing test stops everything**

**[LAPTOP]**

```bash
git checkout main
git pull origin main
```

In VS Code open `src/test/java/com/discoverdevops/library/BookControllerTest.java`. Find `"The Phoenix Project"` and change it to `"Wrong Title"`. Save, then:

```bash
git add .
git commit -m "demo: break a test"
git push origin main
```

**Expected:** Compile and Security scan are green, **Unit tests** is red, and the three jobs after it show as **skipped**. Nothing is built or deployed.

Fix it:

```bash
git revert --no-edit HEAD
git push origin main
```

**Exercise B: Gitleaks catches a committed secret**

**[LAPTOP]**

```bash
git checkout main
git pull origin main
git checkout -b leak-demo
cat > src/main/resources/application-demo.properties <<'EOF'
payments.service.api_key=Zq9Lm3Xp7Rt2Vb8Nc4Kd6Wf1Hj5Sg0Ya
EOF
git add .
git commit -m "demo: accidentally commit a key"
git push -u origin leak-demo
```

**[BROWSER]** Actions tab, left side click the workflow **Library API - End to End CI/CD**, then **Run workflow**, choose branch **leak-demo**, **Run workflow**.

**Expected:** **Security scan** fails with a Gitleaks finding for `generic-api-key`, and later jobs are skipped.

**[LAPTOP]** Clean up:

```bash
git checkout main
git push origin --delete leak-demo
git branch -D leak-demo
```

Remember: deleting the file in a later commit would **not** fix a leaked secret, because Gitleaks scans the whole history. A real leaked secret must be revoked and replaced.

**Exercise C: the quality gate fails on untested code (advanced)**

**[LAPTOP]**

```bash
git checkout main
git pull origin main
cat > src/main/java/com/discoverdevops/library/FineCalculator.java <<'EOF'
package com.discoverdevops.library;

public class FineCalculator {

    public double calculate(int daysLate, boolean member, boolean premium, boolean firstOffence) {
        double fine = daysLate * 2.0;
        if (member) {
            fine = fine * 0.5;
        }
        if (premium) {
            fine = fine * 0.5;
        }
        if (firstOffence) {
            fine = 0;
        }
        if (daysLate > 30) {
            fine = fine + 100;
        }
        if (daysLate > 60) {
            fine = fine + 200;
        }
        if (daysLate > 90) {
            fine = fine + 300;
        }
        return fine;
    }
}
EOF
```

In VS Code open `pom.xml`. Near the top, find the line `<version>1.0.0</version>` that sits directly under `<artifactId>library-api</artifactId>`, and change it to `<version>1.0.1</version>`. (This tells SonarQube it is a new version, so only the new code is judged.) Save, then:

```bash
git add .
git commit -m "demo: add untested code"
git push origin main
```

**Expected:** the **Package, SonarQube analysis and quality gate** job fails with a quality gate error, and the Docker and deploy jobs are skipped. In SonarQube, the project page shows **Quality Gate: Failed** because coverage on new code is too low.

Fix it by removing the demo code:

```bash
git revert --no-edit HEAD
git push origin main
```

---

## Step 17: Cleanup (do not skip)

### Context
Everything you created costs money while it exists. Delete it the same day.

### Concept
**Delete things in the reverse order you created them.** The load balancer must be removed before the cluster's network can be deleted, otherwise the cluster delete gets stuck.

### Lab

**[EC2]** Delete the application, the load balancer and the cluster (about 12 minutes):

```bash
cd ~/library-cicd-lab
bash scripts/cleanup.sh
```

**[BROWSER]** Remove the runner from GitHub: **Settings**, **Actions**, **Runners**, `runner-one`, **Remove**. Then delete the repository secrets and variables you created.

**[EC2]** Leave the server:

```bash
exit
```

**[LAPTOP]** Delete the EC2 server, key pair and security group:

```bash
aws ec2 terminate-instances --region ap-south-1 --instance-ids $INSTANCE_ID
aws ec2 wait instance-terminated --region ap-south-1 --instance-ids $INSTANCE_ID
aws ec2 delete-key-pair --region ap-south-1 --key-name gha-runner-key
aws ec2 delete-security-group --region ap-south-1 --group-id $SG
rm -f gha-runner-key.pem
```

If you opened a new terminal and the variables are gone, delete the instance in the console instead (EC2, Instances, select, Instance state, Terminate), then run the last three lines using `--group-name gha-runner-sg` for the security group.

**[LAPTOP]** Delete the IAM role you created in Step 4 (paste fresh temporary credentials first if yours expired):

```bash
aws iam remove-role-from-instance-profile --instance-profile-name gha-runner-profile --role-name gha-runner-role
aws iam delete-instance-profile --instance-profile-name gha-runner-profile
aws iam detach-role-policy --role-name gha-runner-role --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
aws iam delete-role --role-name gha-runner-role
```

**[BROWSER]** In Docker Hub, delete the `github-actions` access token.

**[LAPTOP] Check that nothing is left running:**

```bash
aws eks list-clusters --region ap-south-1
aws ec2 describe-instances --region ap-south-1 --filters Name=instance-state-name,Values=running --query 'Reservations[].Instances[].InstanceId'
aws ec2 describe-nat-gateways --region ap-south-1 --filter Name=state,Values=available --query 'NatGateways[].NatGatewayId'
aws elb describe-load-balancers --region ap-south-1 --query 'LoadBalancerDescriptions[].LoadBalancerName'
```

**Expected:** all four print empty lists (`[]`).

---

## Appendix A: Troubleshooting

| What you see | Likely cause | Fix |
|---|---|---|
| Job says **Waiting for a runner to pick up this job** | Runner is offline, or the label does not match | **[EC2]** `cd ~/actions-runner && sudo ./svc.sh status`. If stopped: `sudo ./svc.sh start`. Check it is **Idle** in Settings, Actions, Runners |
| No workflow runs after a push | Actions disabled on your fork, wrong branch, or wrong file path | Enable Actions on the Actions tab. The file must be `.github/workflows/cicd.yaml` and you must push to `main` |
| `permission denied ... /var/run/docker.sock` | Docker group not active for the runner | **[EC2]** `exit`, log in again, then `cd ~/actions-runner && sudo ./svc.sh stop && sudo ./svc.sh start` |
| `mvn: command not found` | Tools not installed on the runner | Re-run `bash ~/library-cicd-lab/scripts/install-runner-tools.sh` |
| SonarQube page does not open | Container still starting, wrong IP, or your IP changed | **[EC2]** `sudo docker logs --tail 20 sonar`. If your home IP changed, add it to the security group port 9000 rule |
| SonarQube container keeps stopping, or logs mention `max virtual memory` | Memory setting missing | **[EC2]** `sudo sysctl -w vm.max_map_count=262144` then `sudo docker restart sonar` |
| Sonar container killed (exit 137) | Server out of memory | Use a bigger instance type (t3.large) |
| Sonar step: `Not authorized` | Wrong or missing `SONAR_TOKEN`, or the secret was added to the wrong repository | Generate a new token, update the secret on **your** repository |
| Sonar step: `Quality Gate failed` | Code did not meet the gate | Open the project in SonarQube, read the failing condition, fix the code |
| Gitleaks job fails | A secret exists somewhere in Git history | Revoke and replace the secret. Deleting the file is not enough |
| Gitleaks flags `docs/STUDENT-RUNBOOK.md` | The `.gitleaks.toml` file is missing | Make sure `.gitleaks.toml` exists in the repository root and push again |
| Trivy fails with `429 Too Many Requests` from `repo.maven.apache.org` | Trivy fetched libraries from Maven Central and the shared runner address was rate limited | Use the current `step-02-security-hosted.yaml`, which runs `mvn dependency:resolve` before Trivy. If it still happens, wait 30 minutes or use **Re-run all jobs**, which usually lands on a different machine |
| `Artifact not found` in Docker job | The upload step failed or its name differs | Check the **Upload JAR artifact** step in the previous job succeeded; the name must be `app-jar` |
| Docker login or push `denied` / `unauthorized` | Wrong `DOCKER_HUB_USERNAME`, bad token, or repository missing | Re-create the token with Read & Write; check the variable is exactly `discoverdevops`; create the repo `library-api` |
| Trivy image scan fails the job | A critical fixable vulnerability in the image | Read the log for the package; update the base image or dependency. To only report, change `--exit-code 1` to `--exit-code 0` in the workflow |
| `TOOMANYREQUESTS` from Trivy | Trivy database download rate limit | Re-run the job after a few minutes |
| `kubectl`: `You must be logged in to the server (Unauthorized)` | The runner uses a different AWS identity than the one that created the cluster | **[EC2]** `aws sts get-caller-identity`. It must be the same user you used in Step 7 |
| `kubectl get nodes` fails with a connection error | Cluster is still being created | **[EC2]** `tail -n 5 ~/library-cicd-lab/eks-create.log` and wait |
| Pods show `ImagePullBackOff` | Docker Hub repository is private or the image name is wrong | Make `discoverdevops/library-api` public. **[EC2]** `kubectl describe pod -l app=library-api` shows the exact image name |
| Rollout times out | Pods start but fail health checks | **[EC2]** `kubectl logs -l app=library-api --tail=50` and `kubectl describe pod -l app=library-api` |
| Service `EXTERNAL-IP` stays `<pending>` | Load balancer is still being created | Wait 2 to 3 minutes and run `kubectl get svc library-api` again |
| Application URL does not respond yet | DNS for a new load balancer takes time | Wait 2 to 3 minutes and retry |
| `run-instances` says no default VPC | Your account has no default VPC | Create the instance in the console and choose any public subnet, or run `aws ec2 create-default-vpc --region ap-south-1` |
| **[LAPTOP]** `ExpiredToken` or `InvalidClientTokenId` | Temporary AWS credentials expired or were pasted wrongly | Copy fresh `export` lines from the AWS portal into the same Git Bash window, then run `aws sts get-caller-identity` |
| **[LAPTOP]** `Invalid IAM Instance Profile name` at launch | The instance profile was not ready yet | Wait 20 seconds and run the launch command again |
| **[EC2]** `Unable to locate credentials` | The role was not attached to the server | **[LAPTOP]** `aws ec2 associate-iam-instance-profile --region ap-south-1 --instance-id $INSTANCE_ID --iam-instance-profile Name=gha-runner-profile`, wait a minute, retry |

**Re-running one failed job:** open the run, click **Re-run jobs**, then **Re-run failed jobs**.

---

## Appendix B: Workflow versions at a glance

| Step | File copied into `.github/workflows/cicd.yaml` | Jobs in the pipeline |
|---|---|---|
| 2 | `step-01-compile.yaml` | compile (hosted runner) |
| 3 | `step-02-security-hosted.yaml` | compile, security-scan (hosted runner) |
| 8 | `step-03-self-hosted.yaml` | compile, security-scan (self-hosted) |
| 9 | `step-04-test.yaml` | adds test |
| 11 | `step-05-sonar-package.yaml` | adds quality-and-package |
| 13 | `step-06-docker.yaml` | adds docker-build-scan-push |
| 14 | `step-07-deploy-final.yaml` | adds deploy-to-eks (finished pipeline) |

## Appendix C: The mental model

```
Event (push to main)
  -> Workflow (cicd.yaml)
       -> Job (runs on a Runner, one after another because of needs)
            -> Step (a shell command, or an Action someone else wrote)
```

| Pipeline stage | Tool | What it protects against |
|---|---|---|
| Compile | Maven | Code that does not build |
| Security scan | Gitleaks, Trivy | Leaked secrets, vulnerable libraries |
| Test | JUnit, JaCoCo | Broken behavior, untested code |
| Quality gate | SonarQube | Bugs, vulnerabilities, code smells |
| Image scan | Trivy | Vulnerable container images |
| Deploy | kubectl, EKS | Manual, inconsistent releases |
